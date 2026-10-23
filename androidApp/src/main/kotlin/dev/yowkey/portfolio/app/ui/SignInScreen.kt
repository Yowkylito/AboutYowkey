package dev.yowkey.portfolio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.app.owner.OwnerViewModel

/**
 * The owner's sign-in. The password is the worker's `ADMIN_TOKEN`; it is checked against `GET /admin/summary`
 * and, once the worker takes it, sealed in the Keystore. It is never saved in the screen's saved state.
 */
@Composable
fun SignInScreen(owner: OwnerViewModel) {
    val state by owner.signIn.collectAsState()
    // Plain remember, not rememberSaveable: the password must not land in the saved-instance bundle.
    var password by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val submit = { owner.signIn(password) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                YMark()
                Text("Owner sign-in", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Visits, link clicks, messages and notes from the site, with alerts on this phone. " +
                        "The password is the worker's ADMIN_TOKEN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.error != null,
                    supportingText = state.error?.let { error -> { Text(error) } },
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide" else "Show") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = submit,
                    enabled = password.isNotBlank() && !state.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sign in")
                    }
                }
                TextButton(onClick = owner::showPortfolio, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back to the portfolio")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** The app icon's Y on its orange square, drawn (the same strokes as favicon.svg). */
@Composable
fun YMark(size: Int = 56) {
    Box(
        Modifier
            .size(size.dp)
            .background(YowkeyOrange, RoundedCornerShape((size * 14 / 64).dp))
            .drawBehind {
                val unit = this.size.width / 64f
                val path = Path().apply {
                    moveTo(18 * unit, 16 * unit)
                    lineTo(32 * unit, 34 * unit)
                    lineTo(46 * unit, 16 * unit)
                    moveTo(32 * unit, 34 * unit)
                    lineTo(32 * unit, 50 * unit)
                }
                drawPath(path, Color.White, style = Stroke(width = 7 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            },
    )
}

/** The icon's orange, the same on either theme (the light theme's primary). */
private val YowkeyOrange = Color(0xFFC2410C)
