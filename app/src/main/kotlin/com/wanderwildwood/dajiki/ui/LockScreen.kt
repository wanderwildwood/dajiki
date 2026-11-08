package com.wanderwildwood.dajiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.dajiki.R

/**
 * What shows in place of the writing while the lock waits for the phone's own: the whole app
 * ([app]) or a day's page. The prompt is Android's and comes up by itself; this is what is left
 * when it was put away, with a way to bring it back and, for the diary, a way out to the sheets.
 */
@Composable
fun LockScreen(app: Boolean, onUnlock: () -> Unit, onBack: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextMMD(
            text = stringResource(if (app) R.string.lock_screen_app else R.string.lock_screen_diary),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        TextMMD(
            text = stringResource(R.string.lock_screen_note),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButtonMMD(
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.lock_unlock), style = MaterialTheme.typography.bodySmall) }
        if (onBack != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButtonMMD(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.lock_back), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
