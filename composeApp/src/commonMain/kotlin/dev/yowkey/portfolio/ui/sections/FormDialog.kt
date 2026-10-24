package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yowkey.portfolio.ui.components.CloseGlyph
import dev.yowkey.portfolio.ui.navigation.returnKeysToPage
import kotlinx.coroutines.flow.filterNotNull

/** The Contact section's two forms, each a button on the page that opens the form over it. */
enum class ContactForm { Message, Feedback }

/**
 * Which of the Contact section's forms is open over the page, and the drafts each keeps while it is closed.
 * Remembered above the list/detail switch, as the list's own state is, and the dialogs are drawn over the page rather
 * than inside a row of the list: a row can leave the composition while its dialog is up (the on-screen keyboard
 * shortens the page and its last rows go), and the dialog must not go with it.
 *
 * [open] is saveable UI state rather than the ViewModel's: whether a dialog is up is the page's business, and nothing
 * but the page's buttons and the dialog's own ways out change it. The drafts are the forms' own saveable fields, kept
 * in [drafts] under the form's name while its dialog is closed, so closing a dialog and opening it again finds the
 * draft as it was. What the ViewModel holds (sending, sent, failed) is untouched by either.
 */
@Stable
class ContactForms internal constructor(
    private val opened: MutableState<ContactForm?>,
    internal val drafts: SaveableStateHolder,
) {
    /** The form open over the page; null while neither is. */
    val open: ContactForm? get() = opened.value

    /** The form whose dialog has just closed: its button takes the focus back once the dialog has gone. */
    internal var closed by mutableStateOf<ContactForm?>(null)

    private val openers = ContactForm.entries.associateWith { FocusRequester() }

    /** On [form]'s button in the Contact section, which the focus returns to when its dialog closes. */
    fun opener(form: ContactForm): FocusRequester = openers.getValue(form)

    fun open(form: ContactForm) {
        opened.value = form
    }

    fun close() {
        closed = opened.value ?: return
        opened.value = null
    }
}

/**
 * The page's [ContactForms]. A dialog that closes hands the focus back to the button that opened it, a frame later,
 * once the dialog's layer (or window, on Android) has gone: a keyboard user carries on from where they were instead
 * of from the top of the page. On the web the browser's focus is first handed back to the app's canvas, since a text
 * field that had it typed through a text area that has just gone. Where the button cannot take the focus (a touch
 * screen, or a button no longer on screen), nothing happens.
 */
@Composable
fun rememberContactForms(): ContactForms {
    val drafts = rememberSaveableStateHolder()
    val opened = rememberSaveable { mutableStateOf<ContactForm?>(null) }
    val forms = remember(drafts, opened) { ContactForms(opened, drafts) }
    LaunchedEffect(forms) {
        snapshotFlow { forms.closed }.filterNotNull().collect { form ->
            withFrameNanos { }
            returnKeysToPage()
            try {
                forms.opener(form).requestFocus()
            } catch (e: IllegalStateException) {
                // The button is not in the composition: there is nothing to return to.
            }
            forms.closed = null
        }
    }
    return forms
}

/**
 * A form's place in the Contact section: its [title] with who reads what is sent ([audience]), one line on what it
 * is for, and the button that opens the form over the page. No field shows here: the section stays a line to read,
 * and the form is one press away. The button is the site's own, filled when [primary], outlined otherwise, and
 * carries [opener], so the focus comes back to it when its dialog closes.
 */
@Composable
internal fun FormSection(
    title: String,
    audience: String,
    intro: String,
    action: String,
    primary: Boolean,
    onOpen: () -> Unit,
    opener: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle(title, audience)
        val style = MaterialTheme.typography.bodyMedium
        Text(intro, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readingMeasure(style))
        val button = if (opener != null) Modifier.padding(top = 4.dp).focusRequester(opener) else Modifier.padding(top = 4.dp)
        if (primary) Button(onClick = onOpen, modifier = button) { Text(action) }
        else OutlinedButton(onClick = onOpen, modifier = button) { Text(action) }
    }
}

/**
 * A form over the page, in a modal dialog: the form's [title] and [audience] badge as its heading (and its pane
 * title, which a screen reader announces as it opens), a close button ([closeLabel]), then [content], which scrolls
 * under the heading when it is taller than the window. On a phone the dialog is the window's width less 16 dp each
 * side and as tall as the window allows, so with the keyboard up the fields still scroll into reach; on a wider
 * window it is centred and at most 560 dp wide. The close button, Escape, Back and a tap on the scrim each call
 * [onClose].
 *
 * As it opens, the focus goes to the dialog itself, not to its first field. On a phone a focused field throws the
 * keyboard up over half the dialog before the visitor has read what it asks for (and the first field, the name, is
 * optional anyway); a screen reader starts at the heading rather than in a field; a keyboard reaches the close button,
 * then the first field, with one Tab each. Holding the focus also lets the dialog hear Escape itself, from wherever
 * in it the focus is, a text field included, rather than relying on each platform to turn it into Back.
 */
@Composable
internal fun FormDialog(
    title: String,
    audience: String,
    closeLabel: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = MaterialTheme.colorScheme
        val scroll = rememberScrollState()
        val pane = "$title, $audience"
        val focus = remember { FocusRequester() }
        val close by rememberUpdatedState(onClose)
        Surface(
            modifier = Modifier
                .dialogBounds()
                .semantics { paneTitle = pane }
                // Before the focused field sees it: Escape leaves the dialog, whatever has the focus.
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.Escape) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) close()
                    true
                }
                .focusRequester(focus)
                .focusTarget(),
            shape = RoundedCornerShape(28.dp),
            color = formDialogColor(),
            contentColor = colors.onSurface,
        ) {
            Column {
                val rule = colors.outlineVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        // A hairline under the heading once the form has scrolled beneath it, drawn from the scroll
                        // position in the draw phase, so scrolling never recomposes the heading.
                        .drawWithContent {
                            drawContent()
                            if (scroll.value > 0) {
                                val y = size.height - 0.5.dp.toPx()
                                drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            }
                        }
                        .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormTitle(title, audience, Modifier.weight(1f).padding(vertical = 12.dp))
                    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeLabel }) {
                        CloseGlyph(colors.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scroll)
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
        LaunchedEffect(focus) { focus.requestFocus() }
    }
}

/**
 * The dialog's surface: white over the light page, as the menus are, and a step above the cards on the dark one.
 * The fields' and the unlit stars' [dev.yowkey.portfolio.ui.theme.ControlOutline] keeps 3:1 on it in both themes
 * (FormControlsTest measures it there).
 */
@Composable
internal fun formDialogColor(): Color = MaterialTheme.colorScheme.surfaceContainer

/**
 * The dialog's size: the window less [DialogMargin] each side, at most [DialogMaxWidth] wide, its height the
 * content's up to that. Measured here rather than padded, so the margin stays part of the scrim and a tap on it
 * closes the dialog as a tap anywhere else outside it does.
 */
private fun Modifier.dialogBounds(): Modifier = layout { measurable, constraints ->
    val margins = DialogMargin.roundToPx() * 2
    val widest = DialogMaxWidth.roundToPx()
    val width = if (constraints.hasBoundedWidth) (constraints.maxWidth - margins).coerceIn(0, widest) else widest
    val height = if (constraints.hasBoundedHeight) (constraints.maxHeight - margins).coerceAtLeast(0) else Constraints.Infinity
    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private val DialogMargin = 16.dp
private val DialogMaxWidth = 560.dp
