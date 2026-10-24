package dev.yowkey.portfolio.app.owner

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yowkey.portfolio.R
import dev.yowkey.portfolio.admin.AdminApi
import dev.yowkey.portfolio.admin.AdminApiException
import dev.yowkey.portfolio.admin.AdminMessage
import dev.yowkey.portfolio.admin.AdminNote
import dev.yowkey.portfolio.admin.AdminNotification
import dev.yowkey.portfolio.admin.AdminSignedOutException
import dev.yowkey.portfolio.admin.AdminSummary
import dev.yowkey.portfolio.admin.DailyReport
import dev.yowkey.portfolio.admin.DayStats
import dev.yowkey.portfolio.admin.MessageAction
import dev.yowkey.portfolio.admin.MessageFilter
import dev.yowkey.portfolio.admin.NoteAction
import dev.yowkey.portfolio.admin.after
import dev.yowkey.portfolio.app.OwnerLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** The admin view's sections, in the navigation bar's order. */
enum class Tab(val label: String, val icon: Int) {
    Dashboard("Dashboard", R.drawable.ic_nav_dashboard),
    Messages("Messages", R.drawable.ic_nav_messages),
    Notes("Notes", R.drawable.ic_nav_notes),
    Records("Records", R.drawable.ic_nav_records),
    Settings("Settings", R.drawable.ic_nav_settings),
}

/**
 * Where the app is. [portfolio]: the guest view is up (the owner's own way back into it, or the default when signed
 * out). Otherwise the admin view at [tab], with [messageId] open on Messages, [noteId] picked out on Notes, and
 * [dailyRecords] choosing the reports over the alerts on Records. Signed out, the admin view is the sign-in screen.
 */
@Immutable
data class OwnerNav(
    val portfolio: Boolean = true,
    val tab: Tab = Tab.Dashboard,
    val messageId: Long? = null,
    val noteId: Long? = null,
    val dailyRecords: Boolean = false,
)

/** A list or a screen's data as it loads: what is there (kept while it reloads), whether it is loading, what failed. */
@Immutable
data class Loadable<out T>(val value: T? = null, val loading: Boolean = false, val error: String? = null)

@Immutable
data class Dashboard(val summary: AdminSummary, val days: List<DayStats>)

@Immutable
data class SignInState(val busy: Boolean = false, val error: String? = null)

/** This phone's side of push, for Settings. */
@Immutable
data class PushInfo(val firebase: Boolean, val registered: Boolean, val notificationsAllowed: Boolean)

/**
 * The admin view's state and actions. Every call to the worker is caught: offline or failing, a screen shows what it
 * had with a sentence, never a crash; a 401 anywhere signs out to the sign-in screen with the reason.
 *
 * @param session the owner's session ([OwnerSession.asAccess] on the phone)
 * @param device the phone's side of the alerts, for Settings and the first sign-in
 * @param saved the activity's saved state, which keeps the admin view's place through the process being killed
 */
class OwnerViewModel(
    private val session: OwnerAccess,
    private val device: OwnerDevice,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    val signedIn: StateFlow<Boolean> = session.signedIn

    /** Set when the activity's saved state brought a place back: the process was killed while the app was away. */
    private val restored: OwnerNav? = saved.nav()

    private val _nav = MutableStateFlow(restored ?: OwnerNav())
    val nav: StateFlow<OwnerNav> = _nav.asStateFlow()

    private val _signIn = MutableStateFlow(SignInState())
    val signIn: StateFlow<SignInState> = _signIn.asStateFlow()

    private val _dashboard = MutableStateFlow(Loadable<Dashboard>())
    val dashboard: StateFlow<Loadable<Dashboard>> = _dashboard.asStateFlow()

    private val _messages = MutableStateFlow(Loadable<List<AdminMessage>>())
    val messages: StateFlow<Loadable<List<AdminMessage>>> = _messages.asStateFlow()

    private val _filter = MutableStateFlow(
        saved.get<String>(KEY_FILTER)?.let { name -> MessageFilter.entries.firstOrNull { it.name == name } } ?: MessageFilter.All,
    )
    val filter: StateFlow<MessageFilter> = _filter.asStateFlow()

    private val _notes = MutableStateFlow(Loadable<List<AdminNote>>())
    val notes: StateFlow<Loadable<List<AdminNote>>> = _notes.asStateFlow()

    private val _alerts = MutableStateFlow(Loadable<List<AdminNotification>>())
    val alerts: StateFlow<Loadable<List<AdminNotification>>> = _alerts.asStateFlow()

    private val _reports = MutableStateFlow(Loadable<List<DailyReport>>())
    val reports: StateFlow<Loadable<List<DailyReport>>> = _reports.asStateFlow()

    /** Ids being acted on, so their buttons wait. */
    private val _working = MutableStateFlow(emptySet<Long>())
    val working: StateFlow<Set<Long>> = _working.asStateFlow()

    /**
     * The loads and actions underway. Signing out cancels them, so nothing read with the old token lands on a screen
     * after [clear] has emptied it.
     */
    private val work = SupervisorJob(viewModelScope.coroutineContext[Job])

    /** Per list, a load asked for while one was underway, to run when that one ends (see [load]). Main thread only. */
    private val again = HashMap<MutableStateFlow<*>, () -> Unit>()

    /** Whether the next dashboard load is to fetch the thirty days too (see [refreshDashboard]). Main thread only. */
    private var wholeDashboardDue = false

    private val _notices = Channel<String>(Channel.BUFFERED)

    /** One-off sentences for the snackbar: what an action did, or why it did not. */
    val notices: Flow<String> = _notices.receiveAsFlow()

    /** The owner's day, as the worker counts it (Asia/Manila unless it says otherwise). */
    val zone: StateFlow<ZoneId> = _dashboard
        .map { loaded -> zoneOf(loaded.value?.summary?.timeZone) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, zoneOf(null))

    init {
        // Signed out from anywhere (Settings, or a 401 in the backup check): drop what was loaded.
        viewModelScope.launch {
            signedIn.drop(1).collect { now -> if (!now) clear() }
        }
        // The place and the filter go into the activity's saved state as they change, so a process killed while the
        // app was away comes back where the owner left it rather than on the portfolio. MainActivity does not call
        // start() then (it has saved state), so the place's data loads here.
        _nav.onEach(saved::putNav).launchIn(viewModelScope)
        _filter.onEach { saved[KEY_FILTER] = it.name }.launchIn(viewModelScope)
        if (restored != null && !restored.portfolio) refresh(restored.tab)
    }

    /** The first screen: [link]'s place when a shortcut or notification opened the app, else admin if signed in. */
    fun start(link: OwnerLink?) {
        if (link != null) return open(link) // which loads the place's tab itself
        _nav.value = OwnerNav(portfolio = !signedIn.value)
        if (!_nav.value.portfolio) refresh(_nav.value.tab)
    }

    fun open(link: OwnerLink) {
        _nav.value = when (link) {
            OwnerLink.Dashboard -> OwnerNav(portfolio = false)
            is OwnerLink.Messages -> OwnerNav(portfolio = false, tab = Tab.Messages, messageId = link.id)
            is OwnerLink.Notes -> OwnerNav(portfolio = false, tab = Tab.Notes, noteId = link.id)
            is OwnerLink.Records -> OwnerNav(portfolio = false, tab = Tab.Records, dailyRecords = link.daily)
        }
        if (link is OwnerLink.Messages && link.id != null) _filter.value = MessageFilter.All
        refresh(_nav.value.tab)
    }

    fun showPortfolio() = _nav.update { it.copy(portfolio = true) }

    fun showOwner() {
        _nav.update { it.copy(portfolio = false) }
        refresh(_nav.value.tab)
    }

    fun select(tab: Tab) {
        _nav.update { it.copy(tab = tab, messageId = null, noteId = null) }
        refresh(tab)
    }

    fun showDailyRecords(daily: Boolean) = _nav.update { it.copy(dailyRecords = daily) }

    /** System Back inside the admin view: a message closes, another tab goes to the dashboard. False: leave. */
    fun back(): Boolean {
        val nav = _nav.value
        when {
            nav.messageId != null -> _nav.value = nav.copy(messageId = null)
            nav.tab != Tab.Dashboard -> select(Tab.Dashboard)
            else -> return false
        }
        return true
    }

    fun signIn(password: String) {
        if (_signIn.value.busy || password.isBlank()) return
        _signIn.value = SignInState(busy = true)
        viewModelScope.launch {
            _signIn.value = try {
                val summary = session.signIn(password)
                _dashboard.update { it.copy(value = it.value?.copy(summary = summary)) }
                refresh(_nav.value.tab)
                SignInState()
            } catch (e: AdminSignedOutException) {
                SignInState(error = "That is not the password. It is the worker's ADMIN_TOKEN.")
            } catch (e: AdminApiException) {
                SignInState(error = e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SignInState(error = "Signing in failed (${e.javaClass.simpleName}). Try again.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            session.signOut()
            _nav.value = OwnerNav(portfolio = true)
        }
    }

    /** Reloads [tab]'s data; what is on screen stays while it does. */
    fun refresh(tab: Tab) {
        if (!signedIn.value) return
        when (tab) {
            Tab.Dashboard, Tab.Settings -> refreshDashboard(whole = true)
            Tab.Messages -> load(_messages, { it.messages() }) { markOpenMessageRead() }
            Tab.Notes -> load(_notes, { it.notes() })
            Tab.Records -> {
                load(_alerts, { it.notifications() })
                load(_reports, { it.reports() })
            }
        }
    }

    fun setFilter(filter: MessageFilter) {
        _filter.value = filter
    }

    fun openMessage(id: Long) {
        _nav.update { it.copy(messageId = id) }
        markOpenMessageRead()
    }

    fun closeMessage() = _nav.update { it.copy(messageId = null) }

    fun act(message: AdminMessage, action: MessageAction) {
        perform(message.id, { it.act(message.id, action) }) {
            _messages.update { loaded -> loaded.copy(value = loaded.value?.mapNotNull { if (it.id == message.id) it.after(action, now()) else it }) }
            if (action == MessageAction.Delete) closeMessage()
            refreshDashboard(whole = false) // the unread badge and the hiring count
            when (action) {
                MessageAction.Archive -> "Archived."
                MessageAction.Unarchive -> "Back in the inbox."
                MessageAction.Delete -> "Message deleted."
                MessageAction.Unread -> "Marked unread."
                MessageAction.Read -> null
            }
        }
    }

    fun act(note: AdminNote, action: NoteAction) {
        perform(note.id, { it.act(note.id, action) }) {
            _notes.update { loaded -> loaded.copy(value = loaded.value?.mapNotNull { if (it.id == note.id) it.after(action, now()) else it }) }
            refreshDashboard(whole = false) // the note counts
            when (action) {
                NoteAction.Publish -> "Back on the site."
                NoteAction.Unpublish -> "Taken off the site."
                // Hiding unpins (the worker clears the pin), which the owner should not have to discover.
                NoteAction.Hide -> if (note.isPinned) "Hidden, and no longer pinned." else "Hidden."
                NoteAction.Unhide -> "No longer hidden."
                NoteAction.Delete -> "Note deleted."
                NoteAction.Pin -> "Pinned: it comes first on the site."
                NoteAction.Unpin -> "Unpinned."
                NoteAction.MakePublic -> "On the site now."
            }
        }
    }

    fun clearNoteFocus() = _nav.update { it.copy(noteId = null) }

    fun openAlert(record: AdminNotification) = open(OwnerLink.forAlert(record))

    /** Runs the backup check once now (it also runs every 15 minutes on its own). */
    fun checkNow() {
        device.checkNow()
        notice("Checking for new alerts. Anything new shows as a notification.")
    }

    fun pushInfo(): PushInfo = device.pushInfo()

    /** Asked once, on the first sign-in, for the notification permission (Android 13+). */
    fun shouldAskForNotifications(): Boolean = device.shouldAskForNotifications()

    fun askedForNotifications() = device.askedForNotifications()

    fun notice(text: String) {
        _notices.trySend(text)
    }

    private fun markOpenMessageRead() {
        val id = _nav.value.messageId ?: return
        val message = _messages.value.value?.firstOrNull { it.id == id } ?: return
        if (!message.isRead && id !in _working.value) act(message, MessageAction.Read)
    }

    /**
     * Reloads the dashboard. [whole]: the summary and the thirty days of visits, as the Dashboard and Settings tabs
     * show them. Otherwise the summary alone (the unread badge, the hiring and note counts), which is all that a
     * message or a note action changes, so each action, opening an unread message included, costs one request after
     * it rather than two. A dashboard never loaded loads whole either way.
     */
    private fun refreshDashboard(whole: Boolean) {
        wholeDashboardDue = wholeDashboardDue || whole
        load(_dashboard, { api ->
            // Read when the load starts, so a whole reload asked for during a summary reload is not lost.
            val days = _dashboard.value.value?.days?.takeUnless { wholeDashboardDue }
            wholeDashboardDue = false
            if (days != null) {
                Dashboard(api.summary(), days)
            } else {
                coroutineScope {
                    val summary = async { api.summary() }
                    val thirty = async { api.days(30) }
                    Dashboard(summary.await(), thirty.await())
                }
            }
        })
    }

    /**
     * Loads [target] with [fetch], keeping what it shows while it does. Asked again while a load of it is underway
     * (an action's reload of the dashboard, say), it runs once more when that one ends, however often it was asked:
     * the load underway may have read the worker before the change that asked for it, so dropping the request left
     * the screen stale.
     */
    private fun <T> load(target: MutableStateFlow<Loadable<T>>, fetch: suspend (AdminApi) -> T, then: () -> Unit = {}) {
        if (target.value.loading) {
            again[target] = { load(target, fetch, then) }
            return
        }
        target.update { it.copy(loading = true) }
        viewModelScope.launch(work) {
            target.value = try {
                Loadable(fetch(session.api()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val sentence = failed(e)
                if (target.value.value != null && sentence != null) notice(sentence)
                target.value.copy(loading = false, error = sentence)
            }
            then()
            again.remove(target)?.invoke()
        }
    }

    private fun perform(id: Long, call: suspend (AdminApi) -> Unit, done: () -> String?) {
        if (id in _working.value) return
        _working.update { it + id }
        viewModelScope.launch(work) {
            try {
                call(session.api())
                done()?.let(::notice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)?.let(::notice)
            } finally {
                _working.update { it - id }
            }
        }
    }

    /** The sentence for a failure; a refused token signs out (and needs no sentence here: sign-in shows it). */
    private fun failed(e: Exception): String? = when (e) {
        is AdminSignedOutException -> {
            _signIn.value = SignInState(error = e.message)
            session.expire()
            null
        }
        is AdminApiException -> e.message
        else -> "Something went wrong (${e.javaClass.simpleName}). Pull to try again."
    }

    private fun clear() {
        work.cancelChildren()
        again.clear()
        _dashboard.value = Loadable()
        _messages.value = Loadable()
        _notes.value = Loadable()
        _alerts.value = Loadable()
        _reports.value = Loadable()
        _working.value = emptySet()
    }

    private fun now(): String = Instant.now().toString()

    private fun zoneOf(id: String?): ZoneId =
        runCatching { ZoneId.of(id ?: DEFAULT_ZONE) }.getOrElse { ZoneId.of(DEFAULT_ZONE) }

    private companion object {
        const val DEFAULT_ZONE = "Asia/Manila"
        const val KEY_FILTER = "owner.filter"
    }
}

private const val KEY_PORTFOLIO = "owner.portfolio"
private const val KEY_TAB = "owner.tab"
private const val KEY_MESSAGE = "owner.message"
private const val KEY_NOTE = "owner.note"
private const val KEY_DAILY = "owner.daily"

/** [nav] as plain values, which is all a saved-state Bundle keeps. */
private fun SavedStateHandle.putNav(nav: OwnerNav) {
    this[KEY_PORTFOLIO] = nav.portfolio
    this[KEY_TAB] = nav.tab.name
    this[KEY_MESSAGE] = nav.messageId
    this[KEY_NOTE] = nav.noteId
    this[KEY_DAILY] = nav.dailyRecords
}

/** The place [putNav] kept; null when there is none (a fresh start). */
private fun SavedStateHandle.nav(): OwnerNav? {
    val tab = get<String>(KEY_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: return null
    return OwnerNav(
        portfolio = get<Boolean>(KEY_PORTFOLIO) ?: true,
        tab = tab,
        messageId = get<Long>(KEY_MESSAGE),
        noteId = get<Long>(KEY_NOTE),
        dailyRecords = get<Boolean>(KEY_DAILY) ?: false,
    )
}
