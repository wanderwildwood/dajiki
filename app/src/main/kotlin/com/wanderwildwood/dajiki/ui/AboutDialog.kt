package com.wanderwildwood.dajiki.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.dajiki.BuildConfig
import com.wanderwildwood.dajiki.R

/**
 * What this is, where the writing lives, and where the source is.
 *
 * The line about the folder is here because a stranger cannot safely assume the answer: every
 * other app of this kind keeps its notes inside itself, in a store that leaves with the app,
 * and this one does the opposite.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = "Typewriter ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "Your writing is ordinary text files in the folder you chose. They stay " +
                "there if this app is uninstalled, and any other app can open them.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(text = "GNU General Public License v3", style = MaterialTheme.typography.labelSmall)
        TextMMD(
            text = "Icons from Material Symbols, Apache 2.0",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "github.com/wanderwildwood/dajiki",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        Llama()

        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Close", style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * Straight to the checkout rather than the donation page on the site, which only leads there
 * anyway. A phone with nothing registered for a web address throws, and this says so out loud
 * rather than swallowing it and leaving a press that does nothing with no explanation.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    var dead by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                    )
                }.isSuccess
                dead = !opened
            }
            .padding(vertical = 4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.llama),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        TextMMD(
            text = if (dead) {
                "Nothing here opens web pages — square.link/u/AGu8oT10"
            } else {
                "Feed the llamas"
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
