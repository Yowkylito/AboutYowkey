package dev.yowkey.portfolio.ui.components

import androidx.compose.runtime.Composable

/**
 * Puts plain text on the system clipboard, for the Contact section's "Copy email": a recruiter often pastes the
 * address into a tracking system or a mail client the page cannot open, and a mailto link does nothing where no mail
 * app is set up. Each platform's own clipboard; a failure (no permission, no clipboard) is silent.
 */
@Composable
internal expect fun rememberTextCopier(): (String) -> Unit
