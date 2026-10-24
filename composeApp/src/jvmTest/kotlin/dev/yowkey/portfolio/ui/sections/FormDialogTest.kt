package dev.yowkey.portfolio.ui.sections

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.MessageCopy
import dev.yowkey.portfolio.ui.FeedbackControls
import dev.yowkey.portfolio.ui.FeedbackUiState
import dev.yowkey.portfolio.ui.MessageControls
import dev.yowkey.portfolio.ui.MessageUiState
import dev.yowkey.portfolio.ui.PortfolioScreen
import dev.yowkey.portfolio.ui.PortfolioUiState
import dev.yowkey.portfolio.ui.navigation.Route
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Contact section's forms open over the page: the page shows a title, a line and a button for each, the button
 * opens the form in a dialog with a heading, the dialog's close button, Escape and the scrim close it and give the
 * focus back to the button, a draft outlives closing, and a sent note thanks the visitor inside the dialog. Driven on
 * the real page, scrolled to its end with the End key, as a phone 412 dp wide shows it.
 */
class FormDialogTest {

    private val portfolio = PortfolioParser.parse(File("src/commonMain/composeResources/files/portfolio.json").readText())

    @Test
    fun theSectionShowsAButtonForEachFormAndNoField() = page {
        assertEquals(0, nodes().count { SemanticsProperties.EditableText in it.config }, "no field on the page")
        assertNotNull(button(MessageCopy.OPEN))
        assertNotNull(button(FeedbackCopy.OPEN))
        listOf(MessageCopy.INTRO, FeedbackCopy.INTRO).forEach { line -> assertNotNull(nodes().firstOrNull { line in it.texts }, line) }
        assertNull(nodes().firstOrNull { FeedbackCopy.FORM_INTRO in it.texts }, "what happens to a note is the dialog's to say")
    }

    @Test
    fun eachButtonOpensItsFormUnderAHeading() = page {
        click(button(MessageCopy.OPEN))
        val message = assertNotNull(dialog("${MessageCopy.TITLE}, ${MessageCopy.PRIVATE}"), "the message dialog is open")
        assertEquals(3, message.count { SemanticsProperties.EditableText in it.config }, "name, contact and message")
        assertTrue(message.any { SemanticsProperties.Heading in it.config && it.texts == listOf(MessageCopy.TITLE, MessageCopy.PRIVATE) })
        click(closeButton())
        assertNull(dialog("${MessageCopy.TITLE}, ${MessageCopy.PRIVATE}"), "Close closes it")

        click(button(FeedbackCopy.OPEN))
        val feedback = assertNotNull(dialog("${FeedbackCopy.TITLE}, ${FeedbackCopy.PUBLIC}"), "the feedback dialog is open")
        assertTrue(feedback.any { SemanticsProperties.Heading in it.config && it.texts == listOf(FeedbackCopy.TITLE, FeedbackCopy.PUBLIC) })
        assertTrue(feedback.any { FeedbackCopy.FORM_INTRO in it.texts }, "the dialog says what happens to a note first")
        assertEquals(3, feedback.count { SemanticsProperties.EditableText in it.config }, "name, email and message")
        assertEquals(5, feedback.count { it.label?.let { label -> (1..5).any { star -> label == FeedbackCopy.ratingLabel(star) } } == true })
    }

    @Test
    fun closeEscapeAndTheScrimEachCloseItAndTheFocusReturnsToTheButton() = page {
        click(button(FeedbackCopy.OPEN))
        click(closeButton())
        assertNull(dialog("${FeedbackCopy.TITLE}, ${FeedbackCopy.PUBLIC}"), "Close closes it")
        assertTrue(button(FeedbackCopy.OPEN).focused, "the focus is back on the button")

        click(button(MessageCopy.OPEN))
        assertEquals(0, nodes().count { it.focused && SemanticsProperties.EditableText in it.config }, "no field takes the focus as it opens")
        key(Key.Escape)
        assertNull(dialog("${MessageCopy.TITLE}, ${MessageCopy.PRIVATE}"), "Escape closes it")
        assertTrue(button(MessageCopy.OPEN).focused, "the focus is back on the button")

        click(button(MessageCopy.OPEN))
        // Escape from inside a field too, where a keyboard user is when they want out.
        val field = field(MessageCopy.CONTACT)
        field.config[SemanticsActions.RequestFocus].action?.invoke()
        frames(2)
        assertTrue(field(MessageCopy.CONTACT).focused)
        key(Key.Escape)
        assertNull(dialog("${MessageCopy.TITLE}, ${MessageCopy.PRIVATE}"), "Escape closes it")
        assertTrue(button(MessageCopy.OPEN).focused, "the focus is back on the button")

        click(button(MessageCopy.OPEN))
        tap(Offset(8f, 8f))
        assertNull(dialog("${MessageCopy.TITLE}, ${MessageCopy.PRIVATE}"), "a tap on the scrim closes it")
    }

    @Test
    fun aDraftSurvivesClosingAndOpeningAgain() = page {
        click(button(FeedbackCopy.OPEN))
        type(FeedbackCopy.NAME, "Grace")
        type(FeedbackCopy.MESSAGE, "Lovely site")
        click(closeButton())
        frames(4)
        click(button(FeedbackCopy.OPEN))
        assertEquals("Grace", field(FeedbackCopy.NAME).value)
        assertEquals("Lovely site", field(FeedbackCopy.MESSAGE).value)
        click(closeButton())

        click(button(MessageCopy.OPEN))
        assertEquals("", field(MessageCopy.MESSAGE).value, "each form keeps its own draft")
    }

    @Test
    fun aSentNoteThanksTheVisitorInTheDialogAndTheNextStartsEmpty() = page {
        click(button(FeedbackCopy.OPEN))
        type(FeedbackCopy.MESSAGE, "Lovely site")
        click(nodes().single { it.texts == listOf(FeedbackCopy.SEND) && SemanticsActions.OnClick in it.config })
        assertEquals("Lovely site", sentNotes.single().message)
        val dialog = assertNotNull(dialog("${FeedbackCopy.TITLE}, ${FeedbackCopy.PUBLIC}"), "the dialog stays open")
        assertTrue(dialog.any { FeedbackCopy.THANKS in it.texts }, "the thank-you is in the dialog")
        assertTrue(dialog.none { FeedbackCopy.FORM_INTRO in it.texts }, "and stands alone")
        assertEquals(0, dialog.count { SemanticsProperties.EditableText in it.config })

        click(closeButton())
        assertEquals(FeedbackUiState.Idle, feedbackState, "closing on the thank-you goes back to the form")
        click(button(FeedbackCopy.OPEN))
        assertEquals("", field(FeedbackCopy.MESSAGE).value, "the sent note's words are gone")
    }

    // The page, its state and the moves on it.

    private var feedbackState: FeedbackUiState by mutableStateOf(FeedbackUiState.Idle)
    private var messageState: MessageUiState by mutableStateOf(MessageUiState.Idle)
    private val sentNotes = mutableListOf<FeedbackDraft>()
    private lateinit var scene: ImageComposeScene
    private var time = 0L

    /** The page at 412 by 915 dp, with motion reduced so every move lands at once, scrolled to its end. */
    private fun page(test: FormDialogTest.() -> Unit) {
        ImageComposeScene(412, 915, Density(1f)) {
            PortfolioTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    PortfolioScreen(
                        state = PortfolioUiState.Ready(portfolio),
                        route = Route.Home,
                        platformLabel = "Kotlin/Wasm in your browser",
                        onRetry = {},
                        onOpenProject = {},
                        onBack = {},
                        onUnknownProject = {},
                        onContentShown = {},
                        feedback = FeedbackControls(
                            feedbackState,
                            onSend = { sentNotes += it; feedbackState = FeedbackUiState.Sent },
                            onReset = { feedbackState = FeedbackUiState.Idle },
                        ),
                        message = MessageControls(messageState, onSend = { messageState = MessageUiState.Sent }, onReset = { messageState = MessageUiState.Idle }),
                    )
                }
            }
        }.use { scene ->
            this.scene = scene
            frames(10)
            key(Key.MoveEnd)
            frames(10)
            test()
        }
    }

    private fun frames(count: Int) = repeat(count) {
        Snapshot.sendApplyNotifications()
        scene.render(16_000_000L * ++time)
    }

    /**
     * A press and release of [key]. Compose's own constructor for a key event is internal API (the public way in is a
     * real window's AWT event), which a test pinned to this Compose version may use.
     */
    @OptIn(InternalComposeUiApi::class)
    private fun key(key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        frames(4)
    }

    private fun click(node: SemanticsNode) {
        assertNotNull(node.config[SemanticsActions.OnClick].action).invoke()
        frames(4)
    }

    private fun tap(at: Offset) {
        scene.sendPointerEvent(PointerEventType.Press, at)
        scene.sendPointerEvent(PointerEventType.Release, at)
        frames(4)
    }

    private fun type(label: String, text: String) {
        assertNotNull(field(label).config[SemanticsActions.SetText].action).invoke(AnnotatedString(text))
        frames(2)
    }

    /** Every node, merged as a screen reader reads them, in every layer: the page's and any dialog's. */
    private fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.rootSemanticsNode.withDescendants() }

    /** The nodes of the layer holding the dialog titled [pane], or null while it is not open. */
    private fun dialog(pane: String): List<SemanticsNode>? =
        scene.semanticsOwners.map { it.allNodes() }.firstOrNull { nodes -> nodes.any { it.config.getOrNull(SemanticsProperties.PaneTitle) == pane } }

    private fun button(text: String): SemanticsNode = nodes().single { it.texts == listOf(text) && SemanticsActions.OnClick in it.config }

    private fun closeButton(): SemanticsNode = nodes().single { it.label == FeedbackCopy.CLOSE && SemanticsActions.OnClick in it.config }

    private fun field(label: String): SemanticsNode =
        nodes().single { SemanticsProperties.EditableText in it.config && label in it.texts }

    private fun SemanticsOwner.allNodes(): List<SemanticsNode> = rootSemanticsNode.withDescendants()

    private fun SemanticsNode.withDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.withDescendants() }

    private val SemanticsNode.texts: List<String>
        get() = config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    private val SemanticsNode.label: String?
        get() = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    private val SemanticsNode.value: String?
        get() = config.getOrNull(SemanticsProperties.EditableText)?.text

    private val SemanticsNode.focused: Boolean
        get() = config.getOrNull(SemanticsProperties.Focused) == true
}
