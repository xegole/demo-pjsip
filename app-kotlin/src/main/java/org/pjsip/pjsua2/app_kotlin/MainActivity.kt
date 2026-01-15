package org.pjsip.pjsua2.app_kotlin

import android.Manifest
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.RelativeLayout
import android.media.AudioManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.pjsip.pjsua2.app_kotlin.auth.LoginScreen
import org.pjsip.pjsua2.CallMediaInfo
import org.pjsip.pjsua2.StreamInfo
import org.pjsip.pjsua2.VideoPreview
import org.pjsip.pjsua2.VideoPreviewOpParam
import org.pjsip.pjsua2.VideoWindow
import org.pjsip.pjsua2.VideoWindowHandle
import org.pjsip.pjsua2.app_kotlin.auth.PjiSipData
import org.pjsip.pjsua2.pjsua_state
import java.lang.ref.WeakReference

/* Configs */
const val ACC_REGISTRAR = "sip:pbx2.fexe.co;transport=udp"
const val ACC_PROXY = "sip:pbx2.fexe.co;lr;transport=udp"

// SIP transport listening port
const val SIP_LISTENING_PORT = 8089
const val MSG_SHOW_LOCAL_VIDEO = 3

class MainActivity : ComponentActivity(), Handler.Callback {

    private val viewModel: MainViewModel by viewModels()

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        ActivityCompat.requestPermissions(this@MainActivity, permissions, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        /* Check permissions */
        checkPermissions()

        /* Setup UI handler */
        viewModel.setUiHandler(Handler(this.mainLooper, this))

        /* Observe Speaker State */
        viewModel.isSpeakerOn.observe(this) { isSpeakerOn ->
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (isSpeakerOn) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        }

        /* Set Compose content */
        /* Set Compose content */
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    var pjiSipData by remember { mutableStateOf<PjiSipData?>(null) }
                    
                    Scaffold { innerPadding ->
                        Box(
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            NavHost(navController = navController, startDestination = "login") {
                                composable("login") {
                                    LoginScreen(
                                        onLoginSuccess = { sipDataResponse ->
                                            pjiSipData = PjiSipData(
                                                domain = "pbx2.fexe.co", // Assuming domain is constant or part of response if needed
                                                user = sipDataResponse.account,
                                                secret = sipDataResponse.password,
                                                idUri = "Kotlin <sip:${sipDataResponse.account}@pbx2.fexe.co>",
                                                ext = sipDataResponse.extension
                                            )
                                            navController.navigate("main") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                
                                composable("main") {
                                    // Fallback for preview/testing if null, though flow prevents it
                                    val sipData = pjiSipData ?: PjiSipData(
                                        domain = "pbx2.fexe.co",
                                        user = "100",
                                        secret = "test",
                                        idUri = "Kotlin <sip:100@pbx2.fexe.co>",
                                        ext = "100"
                                    )
                                    MainScreen(
                                        viewModel = viewModel,
                                        extension = sipData.ext,
                                        onStartClick = {
                                            viewModel.initializeLibrary(sipData)
                                        },
                                        onStopClick = {
                                            viewModel.stopLibrary()
                                        },
                                        onCallClick = { extension ->
                                            if (viewModel.getLibraryState() != pjsua_state.PJSUA_STATE_RUNNING)
                                                return@MainScreen

                                            if (viewModel.getCall() == null) {
                                                viewModel.makeCall(extension)
                                            } else {
                                                viewModel.hangupCall()
                                            }
                                        },
                                        onAnswerClick = { viewModel.answerCall() },
                                        onDeclineClick = { viewModel.declineCall() },
                                        onMuteClick = { viewModel.toggleMute() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun handleMessage(m: Message): Boolean {
        if (m.what == MSG_SHOW_LOCAL_VIDEO) {

        } else {
            return false
        }
        return true
    }
}
