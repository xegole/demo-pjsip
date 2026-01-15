package org.pjsip.pjsua2.app_kotlin

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.pjsip.pjsua2.pjsip_inv_state

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    extension: String,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onCallClick: (String) -> Unit,
    onAnswerClick: () -> Unit,
    onDeclineClick: () -> Unit,
    onMuteClick: () -> Unit
) {
    // Observe ViewModel state
    val callInfo by viewModel.callInfoLiveData.observeAsState()
    val libraryStatus by viewModel.libraryStatusLiveData.observeAsState("Libreria SIP no iniciada")
    val isMuted by viewModel.isMuted.observeAsState(false)
    val eventLogs by viewModel.eventLogs.observeAsState(emptyList())

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // State for FancyNumberTextField
    var extensionInput by remember { mutableStateOf("") }

    // Determine button text based on call state
    val callButtonText = when {
        callInfo?.state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> "Llamar"
        callInfo?.state != null && callInfo!!.state > pjsip_inv_state.PJSIP_INV_STATE_NULL -> "Colgar"
        else -> "Llamar"
    }

    // Determine status text


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally // Center all children
    ) {
        Spacer(modifier = Modifier.height(10.dp)) // Space from toolbar

        // Extension Text
        Text(
            text = "Tu numero de Extension: $extension",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Iniciar",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                onClick = onStopClick,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "Detener",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Status text
        // Library Status
        Text(
            text = libraryStatus,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )

        // Call State Text
        callInfo?.stateText?.let {
            Text(
                text = it,
                color = Color.Yellow,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Fancy Number TextField
        if (callInfo?.state == null || callInfo?.state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
            FancyNumberTextField(
                value = extensionInput,
                onValueChange = { extensionInput = it },
                modifier = Modifier.padding(top = 16.dp)
            )

            Button(
                onClick = { onCallClick(extensionInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .height(56.dp), // Make it taller
                enabled = libraryStatus == "Libreria SIP iniciada" && extensionInput.isNotEmpty(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp) // Rounder corners
            ) {
                Text(
                    text = callButtonText,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else if (callInfo?.state == pjsip_inv_state.PJSIP_INV_STATE_INCOMING) {
            // Incoming Call Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onAnswerClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Responder")
                }

                Button(
                    onClick = onDeclineClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Rechazar")
                }
            }
        } else {
            // During call
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { onMuteClick() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isMuted) Color.Gray else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isMuted) "Desmutear" else "Mutear")
                }

                Button(
                    onClick = { onCallClick(extensionInput) }, // Re-using call click which handles hangup if call exists
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Colgar")
                }
            }
        }

        // Logs List
        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Event Logs:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.height(36.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Text("Limpiar", style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0x80000000))
                .padding(8.dp)
        ) {
            items(eventLogs) { log ->
                Text(
                    text = log,
                    color = Color.Green,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
