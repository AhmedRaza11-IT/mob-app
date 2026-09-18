package com.whatsapp.clone.platform

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgoraCallManager private constructor() {

    private var rtcEngine: RtcEngine? = null
    private var currentAppId: String = ""

    private val _isJoined = MutableStateFlow(false)
    val isJoined: StateFlow<Boolean> = _isJoined.asStateFlow()

    private val _remoteUid = MutableStateFlow<Int?>(null)
    val remoteUid: StateFlow<Int?> = _remoteUid.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d(TAG, "Joined channel: $channel with uid: $uid")
            _isJoined.value = true
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "Remote user joined with uid: $uid")
            _remoteUid.value = uid
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Remote user offline with uid: $uid, reason: $reason")
            if (_remoteUid.value == uid) {
                _remoteUid.value = null
            }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            Log.w(TAG, "Agora RTC token will expire soon")
        }
    }

    fun init(context: Context, appId: String) {
        if (rtcEngine != null && currentAppId == appId) return
        try {
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mEventHandler = rtcEventHandler
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            }
            rtcEngine = RtcEngine.create(config).apply {
                enableAudio()
                setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_GAME_STREAMING)
            }
            currentAppId = appId
            Log.d(TAG, "Agora RtcEngine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Agora RtcEngine", e)
        }
    }

    fun joinCall(token: String, channelName: String, uid: Int = 0, isVideo: Boolean = false) {
        val engine = rtcEngine ?: run {
            Log.e(TAG, "RtcEngine not initialized before joinCall")
            return
        }

        _isVideoEnabled.value = isVideo
        if (isVideo) {
            engine.enableVideo()
            engine.startPreview()
        } else {
            engine.disableVideo()
        }

        engine.joinChannel(token, channelName, "", uid)
        _isMuted.value = false
        _isSpeakerOn.value = true
        engine.setEnableSpeakerphone(true)
    }

    fun setupLocalVideo(surfaceView: SurfaceView) {
        rtcEngine?.let { engine ->
            val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            engine.setupLocalVideo(canvas)
        }
    }

    fun setupRemoteVideo(surfaceView: SurfaceView, remoteUid: Int) {
        rtcEngine?.let { engine ->
            val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid)
            engine.setupRemoteVideo(canvas)
        }
    }

    fun toggleMute(isMuted: Boolean) {
        rtcEngine?.muteLocalAudioStream(isMuted)
        _isMuted.value = isMuted
    }

    fun toggleSpeaker(isSpeakerOn: Boolean) {
        rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
        _isSpeakerOn.value = isSpeakerOn
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun leaveCall() {
        rtcEngine?.let { engine ->
            engine.stopPreview()
            engine.leaveChannel()
        }
        _isJoined.value = false
        _remoteUid.value = null
        _isVideoEnabled.value = false
        Log.d(TAG, "Left Agora call channel")
    }

    fun destroy() {
        leaveCall()
        RtcEngine.destroy()
        rtcEngine = null
    }

    companion object {
        private const val TAG = "AgoraCallManager"
        val instance: AgoraCallManager by lazy { AgoraCallManager() }
    }
}
