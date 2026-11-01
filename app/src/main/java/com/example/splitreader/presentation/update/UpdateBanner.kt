package com.example.splitreader.presentation.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.R
import com.example.splitreader.domain.model.UpdateState
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.ui.LibraryTagButton

/** Whether Home shows the banner: an offer not snoozed, a download, or a finished download. */
val UpdateState.showsBanner: Boolean
    get() = when (this) {
        is UpdateState.Available -> !snoozed
        is UpdateState.Downloading, UpdateState.Downloaded -> true
        UpdateState.None -> false
    }

@Composable
fun updateStatusText(state: UpdateState): String? = when (state) {
    is UpdateState.Available -> stringResource(R.string.update_available)
    is UpdateState.Downloading -> state.percent?.let { stringResource(R.string.update_downloading_percent, it) }
        ?: stringResource(R.string.update_downloading)
    UpdateState.Downloaded -> stringResource(R.string.update_ready)
    UpdateState.None -> null
}

/** The Home banner above the greeting. Draws nothing when [showsBanner] is false. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdate: () -> Unit,
    onRestart: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.showsBanner) return
    val palette = LocalReaderPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = palette.bg2,
        border = BorderStroke(1.dp, palette.edge),
    ) {
        // FlowRow: on a phone the actions wrap under the text instead of squeezing it.
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = updateStatusText(state).orEmpty(),
                modifier = Modifier.align(Alignment.CenterVertically).padding(vertical = 6.dp),
                fontFamily = Newsreader,
                fontSize = 15.sp,
                color = palette.ink,
            )
            // weight(1f) + End: when the actions wrap onto their own line (a phone)
            // they sit at the end, not under the start of the text.
            if (state is UpdateState.Available || state == UpdateState.Downloaded) Row(
                modifier = Modifier.align(Alignment.CenterVertically).weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                when (state) {
                    is UpdateState.Available -> {
                        TextButton(onClick = onNotNow) {
                            Text(stringResource(R.string.update_not_now), fontFamily = Newsreader, color = palette.ink2)
                        }
                        LibraryTagButton(stringResource(R.string.update_action), onUpdate, showPlus = false, dense = true, filled = true)
                    }
                    UpdateState.Downloaded ->
                        LibraryTagButton(stringResource(R.string.update_restart), onRestart, showPlus = false, dense = true, filled = true)
                    else -> Unit
                }
            }
        }
    }
}
