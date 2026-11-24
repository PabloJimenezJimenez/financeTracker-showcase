package com.pablojimenez.financetracker.ui.lock

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing

/** The page shown while the app is locked: the app name and one "Unlock" button. */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    LockPage(
        message = stringResource(R.string.app_name),
        action = stringResource(R.string.unlock),
        onAction = onUnlock,
        modifier = modifier,
    )
}

/** The page shown when the phone has no screen lock to unlock the app with. */
@Composable
fun NoScreenLockScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    LockPage(
        message = stringResource(R.string.no_screen_lock),
        action = stringResource(R.string.open_settings),
        onAction = onOpenSettings,
        modifier = modifier,
    )
}

/**
 * A full page on `surface`: a decorative lock icon, [message] and one filled button. It scrolls
 * when large text or landscape make it taller than the screen, so the button stays reachable.
 */
@Composable
private fun LockPage(message: String, action: String, onAction: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Large),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.Large))
                Text(
                    text = message,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.XXLarge))
                Button(onClick = onAction, modifier = Modifier.heightIn(min = Spacing.MinTouchTarget)) {
                    Text(action)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        LockScreen(onUnlock = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun NoScreenLockScreenPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        NoScreenLockScreen(onOpenSettings = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenDarkPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        LockScreen(onUnlock = {})
    }
}
