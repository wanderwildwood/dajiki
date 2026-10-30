package com.wanderwildwood.dajiki.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    // A full screen, not a small panel: held across, a panel leaves room for a few lines and
    // Privacy and Terms would take pages. The body pages on whole rows; Close stays outside it.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
       Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        TextMMD(
            text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(6.dp))
        PagedColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                TextMMD(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            item {
                TextMMD(
                    text = stringResource(R.string.about_privacy_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                TextMMD(text = stringResource(R.string.about_privacy), style = MaterialTheme.typography.labelSmall)
            }
            item {
                TextMMD(text = stringResource(R.string.about_privacy_more), style = MaterialTheme.typography.labelSmall)
            }
            item {
                TextMMD(
                    text = stringResource(R.string.about_terms_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                TextMMD(text = stringResource(R.string.about_terms), style = MaterialTheme.typography.labelSmall)
            }
            item {
                Column(Modifier.padding(top = 10.dp)) {
                    TextMMD(text = stringResource(R.string.about_licence), style = MaterialTheme.typography.labelSmall)
                    TextMMD(text = stringResource(R.string.about_icons), style = MaterialTheme.typography.labelSmall)
                }
            }
            item { LicencesLink() }
            item {
                Spacer(Modifier.height(10.dp))
                Llama()
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.about_close), style = MaterialTheme.typography.bodySmall) }
       }
      }
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 * The site's address sits at the start of the same line and opens the site; the llama and its words
 * open the page.
 *
 * Straight to the checkout rather than the donation page on the site, which only leads there
 * anyway. A phone with nothing registered for a web address throws, and this says so out loud
 * rather than swallowing it and leaving a press that does nothing with no explanation.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    // Which address had nothing to open it, to say so in place of the words.
    var dead by remember { mutableStateOf<String?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.labelSmall,
            // The site's address opens the site, the way the llama beside it opens its page.
            modifier = Modifier
                .clickable {
                    openLink(context, "https://wanderthe.dev", context.getString(R.string.about_no_browser_address, "wanderthe.dev"))
                }
                .heightIn(min = 48.dp)
                .padding(end = 4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    openLink(context, "https://square.link/u/AGu8oT10", context.getString(R.string.about_no_browser_address, "square.link/u/AGu8oT10"))
                }
                .heightIn(min = 48.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(
                text = if (dead != null) {
                    stringResource(R.string.about_no_browser, dead!!)
                } else {
                    stringResource(R.string.about_feed_the_llamas)
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
