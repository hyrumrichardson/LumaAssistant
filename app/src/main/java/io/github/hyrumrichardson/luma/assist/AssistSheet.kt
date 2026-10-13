package io.github.hyrumrichardson.luma.assist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.hyrumrichardson.luma.ui.LumaAvatar

@Composable
fun AssistScreen(
    state: AssistUiState,
    onDismiss: () -> Unit,
    onMic: () -> Unit,
    onKeyboard: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(180)),
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    // Swallow taps so they don't reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                SheetContent(state, onDismiss, onMic, onKeyboard, onSubmit)
            }
        }
    }
}

@Composable
private fun SheetContent(
    state: AssistUiState,
    onDismiss: () -> Unit,
    onMic: () -> Unit,
    onKeyboard: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val listening = state.phase == Phase.Listening || state.phase == Phase.Starting
    val level by animateFloatAsState(if (listening) state.level else 0f, tween(120), label = "level")

    Column(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LumaAvatar(40.dp, glow = level)
            Spacer(Modifier.width(12.dp))
            Text(
                text = when (state.phase) {
                    Phase.Starting -> "One moment…"
                    Phase.Listening -> "Listening…"
                    Phase.Thinking -> "On it…"
                    Phase.Replied -> if (state.success) "Done" else "Hmm"
                    Phase.Typing -> "Type a command"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
        }

        Spacer(Modifier.size(12.dp))

        if (state.phase == Phase.Typing) {
            TypingField(onSubmit)
        } else {
            Column(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalArrangement = Arrangement.Center) {
                if (state.heard.isNotEmpty()) {
                    Text(
                        "“${state.heard}”",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (listening) {
                    Text(
                        "Try “set a timer for 10 minutes”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedContent(
                    targetState = state.reply,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) },
                    label = "reply",
                ) { reply ->
                    if (reply.isNotEmpty()) {
                        Text(
                            reply,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onKeyboard) { Icon(Icons.Rounded.Keyboard, contentDescription = "Type instead") }
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                if (listening) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .scale(1f + 0.3f * level)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                    )
                }
                FilledIconButton(
                    onClick = onMic,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Icon(Icons.Rounded.Mic, contentDescription = "Speak", modifier = Modifier.size(30.dp)) }
            }
            Spacer(Modifier.weight(1f))
            // Balances the keyboard button so the mic stays centred.
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun TypingField(onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Turn off the living room lights in 10 minutes") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit(text) }),
        trailingIcon = {
            IconButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}
