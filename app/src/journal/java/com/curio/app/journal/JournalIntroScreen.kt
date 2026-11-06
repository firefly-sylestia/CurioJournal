package com.curio.app.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.curio.app.data.AppPreferences
import com.curio.app.features.personal.journalInk
import com.curio.app.features.personal.journalPaper
import com.curio.app.features.personal.journalPaperRaised
import com.curio.app.features.personal.personalAccent
import com.curio.app.features.personal.personalAccentInk
import com.curio.app.features.personal.personalOnAccent
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons
import com.curio.app.ui.theme.FrauncesFontFamily

/**
 * THE FIRST OPEN ASKS FOR A NAME.
 *
 * The member's own request: *"when user opens app ask for his or her name, and
 * then enter in top part of the app in large texts say like hello name"*. So this
 * is one page, one field, one door — no steps, no account, nothing that leaves the
 * phone. A blank name is allowed (the greeting then says just "Hello"), and the
 * answer is written the moment the door is pressed, never as the member types.
 */
@Composable
fun JournalIntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val accent = personalAccent()
    val accentInk = personalAccentInk()
    val ink = journalInk()
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun save() {
        AppPreferences.setJournalName(context, name)
        onDone()
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent, modifier = Modifier.width(34.dp)) {
                Box(
                    Modifier
                        .width(34.dp)
                        .height(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CurioIcon(CurioIcons.Note, null, tint = personalOnAccent(), size = 17.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "CURIO JOURNAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.2.sp
                ),
                color = accentInk
            )
        }

        Spacer(Modifier.height(34.dp))
        Text(
            "What should we\ncall you?",
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 45.sp
            ),
            color = ink
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Home greets you by it, and it stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 40) name = it },
            singleLine = true,
            placeholder = {
                Text("Your name", style = MaterialTheme.typography.bodyLarge)
            },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.Medium
            ),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = ink.copy(alpha = 0.16f),
                focusedContainerColor = journalPaper(),
                unfocusedContainerColor = journalPaper(),
                cursorColor = accent,
                focusedTextColor = ink,
                unfocusedTextColor = ink
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = {
                    AppPreferences.setJournalName(context, "")
                    onDone()
                },
                shape = CircleShape,
                color = journalPaperRaised()
            ) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentInk.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { save() },
                shape = CircleShape,
                color = accent,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Start writing",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = personalOnAccent()
                    )
                    CurioIcon(
                        CurioIcons.ArrowForward,
                        null,
                        tint = personalOnAccent(),
                        size = 16.dp
                    )
                }
            }
        }
    }
}
