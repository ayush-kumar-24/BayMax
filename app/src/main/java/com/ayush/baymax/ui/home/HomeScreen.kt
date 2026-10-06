package com.ayush.baymax.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ayush.baymax.agent.QuickChip
import com.ayush.baymax.ui.baymax.BaymaxStage
import com.ayush.baymax.ui.chest.ChestPanel
import com.ayush.baymax.ui.theme.BaymaxTheme

/** Every user action on Home. Kept as one object so the screen stays stateless. */
@Immutable
data class HomeActions(
    val onSend: (String) -> Unit = {},
    val onChip: (QuickChip) -> Unit = {},
    val onMic: () -> Unit = {},
    val onCaseTap: () -> Unit = {},
    val onHeadTap: () -> Unit = {},
    val onPainSelected: (Int) -> Unit = {},
    val onCall: () -> Unit = {},
    val onMessageFriend: () -> Unit = {},
    val onImOkay: () -> Unit = {},
    val onToggleMute: () -> Unit = {},
    val onOpenLog: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenConversation: () -> Unit = {},
)

/**
 * Home (UI spec section 4.1): status bar, Baymax stage with chest panel, captions,
 * quick replies and the text + mic input. Every core action is one tap from here (NFR-11).
 */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = BaymaxTheme.colors
    var inputFocused by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HomeTopBar(
            state = state.agentState,
            online = state.online,
            lowBattery = state.lowBattery,
            muted = state.muted,
            onToggleMute = actions.onToggleMute,
            onOpenLog = actions.onOpenLog,
            onOpenSettings = actions.onOpenSettings,
        )
        BaymaxStage(
            awake = state.awake,
            emergency = state.emergency,
            tilt = state.tilt,
            lowBattery = state.lowBattery,
            listening = state.micListening,
            lookingAtInput = inputFocused,
            blinkTick = state.blinkTick,
            onCaseTap = actions.onCaseTap,
            onHeadTap = actions.onHeadTap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { chestModifier ->
            ChestPanel(
                content = state.chest,
                onPainSelected = actions.onPainSelected,
                onCall = actions.onCall,
                onMessageFriend = actions.onMessageFriend,
                onImOkay = actions.onImOkay,
                modifier = chestModifier,
            )
        }
        CaptionBlock(
            caption = state.caption,
            captionId = state.captionId,
            userLine = state.userLine,
            lowBattery = state.lowBattery,
            onOpenConversation = actions.onOpenConversation,
        )
        QuickChips(state.chips, actions.onChip)
        InputBar(
            listening = state.micListening,
            onSend = actions.onSend,
            onMic = actions.onMic,
            onFocusChange = { inputFocused = it },
        )
    }
}
