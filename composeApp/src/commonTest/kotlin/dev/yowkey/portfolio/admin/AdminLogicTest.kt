package dev.yowkey.portfolio.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminLogicTest {

    private fun message(id: Long, topic: String = "other", read: Boolean = false, archived: Boolean = false) =
        AdminMessage(id, topic = topic, readAt = "x".takeIf { read }, archivedAt = "x".takeIf { archived })

    @Test
    fun theFiltersSplitTheInbox() {
        val inbox = listOf(
            message(1, "job"),
            message(2, "freelance", read = true),
            message(3, "other", read = true),
            message(4, "job", archived = true),
            message(5, "collaboration"),
        )
        fun ids(filter: MessageFilter) = inbox.filter(filter::matches).map { it.id }
        assertEquals(listOf(1L, 2, 3, 5), ids(MessageFilter.All), "archived ones have their own filter")
        assertEquals(listOf(1L, 2), ids(MessageFilter.Hiring))
        assertEquals(listOf(1L, 5), ids(MessageFilter.Unread))
        assertEquals(listOf(4L), ids(MessageFilter.Archived))
    }

    @Test
    fun aMessageChangesAsTheWorkerChangesIt() {
        val now = "2026-10-23T12:00:00.000Z"
        val unread = message(1)
        assertEquals(now, unread.after(MessageAction.Read, now)!!.readAt)
        assertEquals("x", message(2, read = true).after(MessageAction.Read, now)!!.readAt, "reading again keeps the first time")
        assertEquals(null, message(2, read = true).after(MessageAction.Unread, now)!!.readAt)
        assertEquals(now, unread.after(MessageAction.Archive, now)!!.archivedAt)
        assertEquals(null, message(3, archived = true).after(MessageAction.Unarchive, now)!!.archivedAt)
        assertEquals(null, unread.after(MessageAction.Delete, now))
    }

    @Test
    fun aNoteChangesByTheInboxsRules() {
        val now = "2026-10-23T12:00:00.000Z"
        val hidden = AdminNote(1, consent = true, hiddenAt = "x")
        assertEquals(AdminNote(1, consent = true, publishedAt = now, hiddenAt = null), hidden.after(NoteAction.Publish, now))
        assertEquals(now, AdminNote(1, consent = true).after(NoteAction.Unpublish, now)!!.hiddenAt, "Unpublish hides")
        assertTrue(AdminNote(1, consent = true).isPublic, "public as it arrives")
        assertFalse(hidden.isPublic)
        assertFalse(AdminNote(1, consent = false).isPublic, "sent before notes were public, without the tick")
        assertEquals(now, AdminNote(1).after(NoteAction.Hide, now)!!.hiddenAt)
        assertEquals(null, hidden.after(NoteAction.Unhide, now)!!.hiddenAt)
        assertEquals(null, hidden.after(NoteAction.Delete, now))
    }

    @Test
    fun aPinIsSetAndClearedAsTheWorkerDoesIt() {
        val now = "2026-10-23T12:00:00.000Z"
        val public = AdminNote(1, consent = true)
        val pinned = public.after(NoteAction.Pin, now)!!
        assertEquals(now, pinned.pinnedAt)
        assertTrue(pinned.isPinned)
        assertTrue(pinned.isPublic, "pinning leaves it on the site")
        assertEquals(public, pinned.after(NoteAction.Unpin, now), "unpinning clears only the pin")
        assertFalse(public.after(NoteAction.Unpin, now)!!.isPinned, "unpinning a note that is not pinned is harmless")
    }

    @Test
    fun hidingANoteUnpinsItAndUnhidingDoesNotPinItAgain() {
        val now = "2026-10-23T12:00:00.000Z"
        val pinned = AdminNote(1, consent = true, pinnedAt = "2026-10-22T08:00:00.000Z")
        val hidden = pinned.after(NoteAction.Hide, now)!!
        assertEquals(now, hidden.hiddenAt)
        assertFalse(hidden.isPinned, "hide clears pinned_at")
        assertFalse(pinned.after(NoteAction.Unpublish, now)!!.isPinned, "the 1.0 app's Unpublish is a hide")
        assertFalse(hidden.after(NoteAction.Unhide, now)!!.isPinned)
        assertTrue(hidden.after(NoteAction.Unhide, now)!!.isPublic)
        assertEquals("2026-10-22T08:00:00.000Z", pinned.after(NoteAction.Unhide, now)!!.pinnedAt, "unhide leaves a pin alone")
    }

    @Test
    fun thePinnedComeFirstNewestPinFirstThenTheRestNewestFirst() {
        val notes = listOf(
            AdminNote(3, consent = true),
            AdminNote(9, consent = true),
            AdminNote(2, consent = true, pinnedAt = "2026-10-23T09:00:00.000Z"),
            AdminNote(5, consent = false),
            AdminNote(4, consent = true, pinnedAt = "2026-10-23T11:00:00.000Z"),
            AdminNote(7, consent = true, hiddenAt = "x"),
        )
        assertEquals(listOf(4L, 2, 9, 7, 5, 3), pinnedFirst(notes).map { it.id })
        assertEquals(listOf(9L, 7, 5, 4, 3, 2), pinnedFirst(notes.map { it.copy(pinnedAt = null) }).map { it.id }, "no pins: newest first")
        assertEquals(emptyList<AdminNote>(), pinnedFirst(emptyList()))

        // A pin made on the phone moves its note to the top, and unpinning puts it back in its place.
        val now = "2026-10-23T12:00:00.000Z"
        val repinned = pinnedFirst(notes.map { if (it.id == 3L) it.after(NoteAction.Pin, now)!! else it })
        assertEquals(listOf(3L, 4, 2, 9, 7, 5), repinned.map { it.id })
        val unpinned = pinnedFirst(repinned.map { if (it.id == 4L) it.after(NoteAction.Unpin, now)!! else it })
        assertEquals(listOf(3L, 2, 9, 7, 5, 4), unpinned.map { it.id })
    }

    @Test
    fun theChartHasEveryDayUpToTodayWithTheMissingOnesAtZero() {
        val days = listOf(DayStats("2026-10-23", 8), DayStats("2026-10-21", 3), DayStats("2026-09-01", 99))
        val series = visitSeries(days, today = "2026-10-23", count = 4)
        assertEquals(
            listOf(
                DayVisits("2026-10-20", 0, false),
                DayVisits("2026-10-21", 3, false),
                DayVisits("2026-10-22", 0, false),
                DayVisits("2026-10-23", 8, true),
            ),
            series,
        )
        assertEquals(30, visitSeries(days, "2026-10-23").size)
        assertEquals("2026-09-24", visitSeries(days, "2026-10-23").first().day)
    }

    @Test
    fun theDayArithmeticCrossesMonthsYearsAndLeapDays() {
        assertEquals(0L, epochDay("1970-01-01"))
        assertEquals("1970-01-01", isoDay(0))
        for (day in listOf("2024-02-28", "2024-02-29", "2024-03-01", "2025-12-31", "2026-01-01", "2000-02-29", "1999-12-31")) {
            assertEquals(day, isoDay(epochDay(day)!!))
        }
        assertEquals(epochDay("2024-03-01")!! - 2, epochDay("2024-02-28"), "2024 is a leap year")
        assertEquals("2026-01-01", isoDay(epochDay("2025-12-31")!! + 1))
        assertEquals(null, epochDay("23 October"))
    }

    @Test
    fun theTopCountriesAddUpOverTheDays() {
        val days = listOf(
            DayStats("2026-10-23", countries = listOf(CountryCount("PH", 6), CountryCount("US", 2))),
            DayStats("2026-10-22", countries = listOf(CountryCount("us", 5), CountryCount("SG", 2), CountryCount("", 9))),
        )
        assertEquals(listOf(CountryCount("US", 7), CountryCount("PH", 6), CountryCount("SG", 2)), topCountries(days))
        assertEquals(listOf(CountryCount("US", 7)), topCountries(days, limit = 1))
    }

    @Test
    fun aCountryCodeBecomesItsFlag() {
        assertEquals("🇵🇭", flagOf("PH"))
        assertEquals("🇺🇸", flagOf("us"))
        assertEquals("", flagOf("XX"))
        assertEquals("", flagOf("T1"))
        assertEquals("", flagOf(""))
    }

    private fun record(id: Long) = AdminNotification(id, kind = "message", title = "t$id")

    @Test
    fun theFirstCheckOnlyNotesWhereTheRecordsStand() {
        assertEquals(PollPlan(emptyList(), 13), planPoll(cursor = null, fetched = listOf(record(13), record(12)), shown = emptySet()))
        assertEquals(PollPlan(emptyList(), 0), planPoll(cursor = null, fetched = emptyList(), shown = emptySet()))
    }

    @Test
    fun laterChecksShowWhatIsNewOldestFirstAndSkipWhatAPushShowed() {
        val plan = planPoll(cursor = 10, fetched = listOf(record(13), record(11), record(12), record(9)), shown = setOf(12))
        assertEquals(listOf(11L, 13), plan.show.map { it.id })
        assertEquals(13, plan.cursor)
        assertEquals(PollPlan(emptyList(), 10), planPoll(cursor = 10, fetched = emptyList(), shown = emptySet()))
    }

    @Test
    fun theShownRecordKeepsTheNewestIds() {
        assertEquals(listOf(1L, 3, 2), rememberShown(listOf(1, 2, 3), 2))
        assertEquals(listOf(3L, 4), rememberShown(listOf(1, 2, 3), 4, keep = 2))
    }
}
