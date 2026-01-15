package org.pjsip.pjsua2.app_kotlin

import android.os.Handler
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.LogEntry
import org.pjsip.pjsua2.LogWriter
import org.pjsip.pjsua2.OnCallMediaEventParam
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.app_kotlin.auth.PjiSipData
import org.pjsip.pjsua2.pj_log_decoration
import org.pjsip.pjsua2.pjmedia_event_type
import org.pjsip.pjsua2.pjmedia_srtp_use
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsip_transport_type_e
import org.pjsip.pjsua2.pjsua2
import org.pjsip.pjsua2.pjsua_call_media_status
import org.pjsip.pjsua2.pjsua_state

class MainViewModel : ViewModel() {

    /* Global objects */
    private object PjiSipManager {
        /* Maintain reference to avoid auto garbage collecting */
        lateinit var logWriter: MyLogWriter

        /* Message handler for updating UI */
        lateinit var uiHandler: Handler
        var call: MyCall? = null
    }

    val acc = MyAccount()
    val endpoint = Endpoint()

    /* LiveData for UI state */
    private val _callInfoLiveData = MutableLiveData<CallInfo?>()
    val callInfoLiveData: LiveData<CallInfo?> = _callInfoLiveData

    private val _libraryStatusLiveData = MutableLiveData<String>()
    val libraryStatusLiveData: LiveData<String> = _libraryStatusLiveData

    private val _isSpeakerOn = MutableLiveData<Boolean>()
    val isSpeakerOn: LiveData<Boolean> = _isSpeakerOn

    private val _isMuted = MutableLiveData<Boolean>(false)
    val isMuted: LiveData<Boolean> = _isMuted

    private val _eventLogs = MutableLiveData<List<String>>(emptyList())
    val eventLogs: LiveData<List<String>> = _eventLogs

    private fun addLog(message: String) {
        val currentList = _eventLogs.value?.toMutableList() ?: mutableListOf()
        currentList.add(message)
        _eventLogs.postValue(currentList)
    }

    fun clearLogs() {
        _eventLogs.postValue(emptyList())
    }

    /* Log writer, redirects logs to stdout */
    internal class MyLogWriter(private val logCallback: (String) -> Unit) : LogWriter() {
        override fun write(entry: LogEntry) {
            Log.d("PjsipLog", entry.msg)
            logCallback(entry.msg)
        }
    }

    /* Account implementation */
    inner class MyAccount : Account() {
        override fun onIncomingCall(prm: OnIncomingCallParam) {
            val call = MyCall(this, prm.callId)

            if (PjiSipManager.call != null) {
                /* Already in a call, busy */
                val callOpParam = CallOpParam(true)
                callOpParam.statusCode = pjsip_status_code.PJSIP_SC_BUSY_HERE
                try {
                    call.answer(callOpParam)
                } catch (e: Exception) {
                    println(e)
                }
                return
            }

            PjiSipManager.call = call
            Log.d("CallMediaInfo", "Incoming call")
            addLog("Incoming call")

            /* Notify UI of incoming call by manually triggering update if needed, 
               but onCallState should handle it independently. 
               However, to be sure we have the info: */
            try {
                val ci = getCallInfo(call)
                if (ci != null) {
                    _callInfoLiveData.postValue(ci)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun getCallInfo(call: Call): CallInfo? {
        var callInfo: CallInfo? = null
        try {
            callInfo = call.info
        } catch (e: Exception) {
            println("Failed getting call info: $e")
        }
        return callInfo
    }

    /* Call implementation */
    inner class MyCall(acc: Account, callId: Int) : Call(acc, callId) {

        override fun onCallState(prm: OnCallStateParam?) {
            val callInfo: CallInfo = getCallInfo(this) ?: return
            Log.d("CallMediaInfo", callInfo.stateText)

            if (callInfo.state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                Log.d("CallMediaInfo", callInfo.lastReason)
            }


            endpoint.utilLogWrite(3, "MyCall", "Call state changed to: " + callInfo.stateText)
            addLog("Call State: ${callInfo.stateText}")

            // Update LiveData instead of sending message
            _callInfoLiveData.postValue(callInfo)

            if (callInfo.state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                PjiSipManager.call = null
                _isSpeakerOn.postValue(false)
                endpoint.utilLogWrite(3, "MyCall", this.dump(true, ""))
            }
        }

        override fun onCallMediaState(prm: OnCallMediaStateParam?) {
            val callInfo: CallInfo = getCallInfo(this) ?: return
            val mediaInfoList = callInfo.media
            Log.d("CallMediaInfo", "onCallMediaState")
            for (i in mediaInfoList.indices) {
                val callMediaInfo = mediaInfoList[i]
                if (callMediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                    (callMediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                            callMediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)
                ) {
                    try {
                        val audioMedia = getAudioMedia(i)
                        endpoint.audDevManager().captureDevMedia.startTransmit(audioMedia)
                        audioMedia.startTransmit(endpoint.audDevManager().playbackDevMedia)

                        // Force speaker on when audio is active
                        _isSpeakerOn.postValue(true)
                    } catch (e: Exception) {

                        // Force speaker on when audio is active
                        _isSpeakerOn.postValue(true)

                        // Reset mute state
                        _isMuted.postValue(false)
                    } catch (e: Exception) {
                        Log.d("CallMediaInfo", "Failed connecting media ports" + e.message)
                    }
                }
            }
        }

        override fun onCallMediaEvent(callMediaEventParam: OnCallMediaEventParam?) {
            Log.d("CallMediaInfo", callMediaEventParam?.ev?.type.toString())
            if (callMediaEventParam!!.ev.type == pjmedia_event_type.PJMEDIA_EVENT_FMT_CHANGED) {
                val callInfo: CallInfo = getCallInfo(this) ?: return
                if (callMediaEventParam.medIdx < 0 || callMediaEventParam.medIdx >= callInfo.media.size)
                    return

                /* Check if this event is from incoming video */
                val callMediaInfo = callInfo.media[callMediaEventParam.medIdx.toInt()]
                if (callMediaInfo.type != pjmedia_type.PJMEDIA_TYPE_VIDEO ||
                    callMediaInfo.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                    callMediaInfo.videoIncomingWindowId == pjsua2.INVALID_ID
                )
                    return

                /* Currently this is a new incoming video */
                println("Got remote video format change = " + callMediaEventParam.ev.data.fmtChanged.newWidth + "x" + callMediaEventParam.ev.data.fmtChanged.newHeight)
            }
        }
    }

    fun setUiHandler(handler: Handler) {
        PjiSipManager.uiHandler = handler
    }

    fun getCall(): MyCall? = PjiSipManager.call

    fun initializeLibrary(sipData: PjiSipData) {
        if (endpoint.libGetState() > pjsua_state.PJSUA_STATE_NULL) {
            return
        }

        val epConfig = EpConfig()

        /* Setup our log writer */
        val logCfg = epConfig.logConfig
        PjiSipManager.logWriter = MyLogWriter(::addLog)
        logCfg.writer = PjiSipManager.logWriter
        logCfg.decor = logCfg.decor and
                (pj_log_decoration.PJ_LOG_HAS_CR or
                        pj_log_decoration.PJ_LOG_HAS_NEWLINE).inv().toLong()

        /* Create & init PJSUA2 */
        try {
            endpoint.libCreate()
            endpoint.libInit(epConfig)
            startLibrary(sipData)
        } catch (e: Exception) {
            println(e)
        }
    }

    fun startLibrary(sipData: PjiSipData) {
        if (endpoint.libGetState() != pjsua_state.PJSUA_STATE_INIT) {
            return
        }

        /* Create transports and account. */
        try {
            val sipTpConfig = TransportConfig()
            sipTpConfig.port = SIP_LISTENING_PORT.toLong()
            endpoint.transportCreate(
                pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,
                sipTpConfig
            )

            endpoint.transportCreate(
                pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,
                TransportConfig()
            )
            sipTpConfig

            val accCfg = AccountConfig()
            accCfg.idUri = sipData.idUri
            accCfg.natConfig.iceEnabled = true
//            accCfg.natConfig.iceNoRtcp = true
//            accCfg.mediaConfig.srtpUse = pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL
//            accCfg.mediaConfig.srtpSecureSignaling = 0
            accCfg.regConfig.registrarUri = ACC_REGISTRAR
            accCfg.sipConfig.authCreds.add(
                AuthCredInfo(
                    "Digest",
                    "*",
                    sipData.user,
                    0,
                    sipData.secret
                )
            )
//            accCfg.sipConfig.proxies.add(ACC_PROXY)
            acc.create(accCfg, true)
        } catch (e: Exception) {
            println(e)
        }

        /* Start PJSUA2 */
        try {
            endpoint.libStart()
        } catch (e: Exception) {
            println(e)
        }
        _libraryStatusLiveData.postValue("Libreria SIP iniciada")
        addLog("Libreria SIP iniciada")

        /* Prioritize AMR-WB */
        try {
            endpoint.codecSetPriority("AMR-WB", 255)
            endpoint.codecSetPriority("AMR/8000", 254)
        } catch (e: Exception) {
            println(e)
        }
    }

    fun makeCall(ext: String) {
        if (endpoint.libGetState() != pjsua_state.PJSUA_STATE_RUNNING)
            return

        if (PjiSipManager.call == null) {
            try {
                /* Make call (to itself) */
                val call = MyCall(acc, -1)
                val prm = CallOpParam(true)
                val dstUri = "MicroSIP <sip:${ext}@pbx2.fexe.co>"
                call.makeCall(dstUri, prm)
                PjiSipManager.call = call
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    fun toggleMute() {
        val call = PjiSipManager.call ?: return
        val currentMuteState = _isMuted.value ?: false
        val newMuteState = !currentMuteState

        try {
            val callInfo = getCallInfo(call) ?: return
            val mediaInfoList = callInfo.media

            for (i in mediaInfoList.indices) {
                val callMediaInfo = mediaInfoList[i]
                if (callMediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                    (callMediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                            callMediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)
                ) {
                    val audioMedia = call.getAudioMedia(i)
                    if (newMuteState) {
                        endpoint.audDevManager().captureDevMedia.stopTransmit(audioMedia)
                    } else {
                        endpoint.audDevManager().captureDevMedia.startTransmit(audioMedia)
                    }
                }
            }
            _isMuted.postValue(newMuteState)
        } catch (e: Exception) {
            println("Failed to toggle mute: $e")
        }
    }

    fun answerCall() {
        val call = PjiSipManager.call ?: return
        val prm = CallOpParam(true)
        prm.statusCode = pjsip_status_code.PJSIP_SC_OK
        try {
            call.answer(prm)
        } catch (e: Exception) {
            println(e)
        }
    }

    fun declineCall() {
        if (PjiSipManager.call != null) {
            val prm = CallOpParam(true)
            prm.statusCode = pjsip_status_code.PJSIP_SC_DECLINE
            try {
                PjiSipManager.call!!.hangup(prm)
            } catch (e: Exception) {
                try {
                    endpoint.hangupAllCalls()
                } catch (ex: Exception) {
                    println(ex)
                }
            }
        }
    }

    fun hangupCall() {
        try {
            endpoint.hangupAllCalls()
        } catch (e: Exception) {
            println(e)
        }
    }

    fun stopLibrary() {
        if (endpoint.libGetState() == pjsua_state.PJSUA_STATE_NULL)
            return

        /* Destroy PJSUA2 */
        try {
            endpoint.hangupAllCalls()
            endpoint.libDestroy()
        } catch (e: Exception) {
            println(e)
        }
        _libraryStatusLiveData.postValue("Libreria SIP detenida")
        addLog("Libreria SIP detenida")
    }

    fun getLibraryState(): Int {
        return endpoint.libGetState()
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup when ViewModel is destroyed
        if (endpoint.libGetState() != pjsua_state.PJSUA_STATE_NULL) {
            try {
                endpoint.hangupAllCalls()
                endpoint.libDestroy()
            } catch (e: Exception) {
                println(e)
            }
        }
    }
}
