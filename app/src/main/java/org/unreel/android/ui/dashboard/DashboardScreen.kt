package org.unreel.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "UNREEL",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Counter Display
            Text(
                text = "${state.blockedTodayCount}",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Text(
                text = "Reels blocked today (≈ ${state.estimatedMinutesSaved} min saved)",
                fontSize = 14.sp,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(24.dp))

            // Master Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Block Instagram Reels",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = if (state.isReelsFilterEnabled) "Active" else "Disabled",
                        fontSize = 13.sp,
                        color = if (state.isReelsFilterEnabled) Color.Green else Color.Gray
                    )
                }

                Switch(
                    checked = state.isReelsFilterEnabled,
                    onCheckedChange = { viewModel.setReelsFilterEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Pause section
            if (state.isPaused) {
                val mins = state.remainingPauseSeconds / 60
                val secs = state.remainingPauseSeconds % 60
                val formattedTime = "%02d:%02d".format(mins, secs)

                Text(
                    text = "Paused ($formattedTime remaining)",
                    fontSize = 14.sp,
                    color = Color.Yellow
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.resumeNow() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(text = "Resume Filtering Now", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.pauseFor15Minutes() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.DarkGray,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(text = "Pause for 15 Minutes", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
