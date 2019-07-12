package com.vng.zalo.assistant.testasr

//import service.StreamVoiceGrpc
//import service.TextReply
//import service.VoiceRequest


import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.util.Log
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import voicebot.VoiceBotConfig
import voicebot.VoiceBotGrpc
import voicebot.VoiceBotRequest
import voicebot.VoiceBotResponse
import javax.net.ssl.SSLException

class VoiceClient(private val channel: ManagedChannel) {
    private val asyncStub: VoiceBotGrpc.VoiceBotStub

    private lateinit var recognitionUICallback: RecognitionUICallback
    private lateinit var audioManager: AudioManager
    private lateinit var playbackAttributes: AudioAttributes

    private var focusRequest: AudioFocusRequest? = null

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> stopStreaming()
            else -> {
            }
        }
    }
    private val emptyHandler = Handler()

    @SuppressLint("HandlerLeak")
    private val handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (recognitionUICallback == null) return
            val msgIfAny = if (msg.obj != null) msg.obj.toString() else ""
            when (msg.what) {
                START_CODE -> recognitionUICallback.onStartRec()
                STOP_CODE -> recognitionUICallback.onStopRec()
                ERR_CODE -> {
                    if (msgIfAny == "") return
                    recognitionUICallback.onFailed(msgIfAny)
                }
                UPDATE_CODE -> {
                    if (msgIfAny == "") return
                    recognitionUICallback.onUpdateText(msgIfAny)
                }
                FINISH_CODE -> {
                    if (msgIfAny == "") return
                    recognitionUICallback.onFinal(msgIfAny)
                }
            }
        }
    }

    private var timeOutConnecting = 5;

    private var recording = false
    private var connecting = false

    internal var recorder: AudioRecord? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val request: StreamObserver<VoiceBotRequest>? = null
    internal var minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var lastServerResponseTime: Long = 0
    private var lastResult: String? = null

    @Throws(SSLException::class)
    constructor(context: Context, uiCallback: RecognitionUICallback) : this(
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
//            .useTransportSecurity()
            //.sslSocketFactory(SSLContext)
            .build()
    ) {
        NORMALIZED = true
        SINGLE_TYPE = true
        recognitionUICallback = uiCallback
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    init {
        val header = Metadata()
        asyncStub = MetadataUtils.attachHeaders<VoiceBotGrpc.VoiceBotStub>(VoiceBotGrpc.newStub(channel), header)
    }

    fun stopStreaming() {
        if (recording) {
            recording = false
            handler.sendMessage(Message.obtain(handler, STOP_CODE))
        }
    }

    fun startStreaming() {
        lastResult = ""
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(afChangeListener, emptyHandler)
                .build()

            val res = audioManager.requestAudioFocus(focusRequest!!)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                readyToAsr()
            }
        } else {
            val result = audioManager.requestAudioFocus(
                afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                readyToAsr()
            }
        }
    }

    private fun readyToAsr() {
        val streamThread = Thread {
            recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize)

            if (recorder!!.state != AudioRecord.STATE_INITIALIZED) {
                recognitionUICallback!!.onFailed("Audio Record can't initialize!")
                return@Thread
            }

            if (minBufSize == AudioRecord.ERROR || minBufSize == AudioRecord.ERROR_BAD_VALUE) {
                minBufSize = SAMPLE_RATE * 2
            }

            //                    if (minBufSize == AudioRecord.ERROR || minBufSize == AudioRecord.ERROR_BAD_VALUE) {
            //                        minBufSize = sampleRate * 2;
            //                    }

            val audioBuffer = ByteArray(minBufSize)

            val responseObserver = object : StreamObserver<VoiceBotResponse> {
                //Định nghĩa sẽ làm gì với TextReply asr_response trả về:
                override fun onNext(voiceBotResponse: VoiceBotResponse) {
                    // Tin nhắn đầu tiên không chứa textAsr, server dùng để báo hiệu đã thông kết nối với client
                    connecting = true
                    var textAsr = voiceBotResponse.textAsr
                    if (textAsr == null) return
                    lastServerResponseTime = System.currentTimeMillis()
                    val resultFinal = voiceBotResponse.final
                    handler.sendMessage(Message.obtain(handler, UPDATE_CODE, textAsr))

                    // Only when single
                    if (resultFinal) {
                        // Text trả lời của bot
                        val textBot = voiceBotResponse.text
                        // Audio trả lời của bot
                        val audioBot = voiceBotResponse.audioContent
                        handler.sendMessage(Message.obtain(handler, FINISH_CODE, textAsr))
                    }
                }

                //Định nghĩa các việc sẽ làm nếu server trả về lỗi nào đó
                override fun onError(throwable: Throwable) {
                    // Already stopAsr
                    if (!recording) return
                    handler.sendMessage(
                        Message.obtain(
                            handler, ERR_CODE,
                            throwable.toString()
                        )
                    )
                    stopStreaming()
                }

                //Định nghĩa những việc sẽ làm khi server kết thúc stream
                override fun onCompleted() {
                    // Already stopAsr
                    if (!recording) return

                    stopStreaming()

                    if (TextUtils.isEmpty(lastResult)) {
                        handler.sendMessageDelayed(Message.obtain(handler, FINISH_CODE, ""), 300)
                    } else {
                        // Multi type will not end in onNext so we analyze here
                        if (!SINGLE_TYPE) {
                            handler.sendMessage(Message.obtain(handler, FINISH_CODE, lastResult))
                        }
                    }
                }
            }

            val request = asyncStub.callToBot(responseObserver)

            handler.sendMessage(Message.obtain(handler, START_CODE))

            recorder!!.startRecording()
            lastServerResponseTime = System.currentTimeMillis()
            recording = true

            val callCenter = "cc1"
            Log.d("quyendb", "Try to call center cc1")
            request.onNext(
                VoiceBotRequest.newBuilder().setVoicebotConfig(
                    VoiceBotConfig.newBuilder().setCallCenterCode(callCenter)
                ).build()
            )

            Log.d("quyendb", "Waitting for connection")
            var currentTime = System.currentTimeMillis()
            while (!connecting)
                if (System.currentTimeMillis() - currentTime >= timeOutConnecting * 1000)
                    break

            Log.d("quyendb", "Connect to server -  " + connecting)
            while (recording && connecting) {
                if (recorder!!.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    handler.sendMessage(
                        Message.obtain(
                            handler, ERR_CODE,
                            "Record error"
                        )
                    )
                    handler.sendMessageDelayed(Message.obtain(handler, STOP_CODE), 300)

                    recording = false
                } else {
                    recorder!!.read(audioBuffer, 0, audioBuffer.size)
                    request.onNext(VoiceBotRequest.newBuilder().setAudioContent(ByteString.copyFrom(audioBuffer)).build())
                }

                if (System.currentTimeMillis() - lastServerResponseTime > CLIENT_TIME_OUT) {
                    handler.sendMessage(
                        Message.obtain(
                            handler, ERR_CODE,
                            "Server ASR not response!"
                        )
                    )
                    handler.sendMessage(Message.obtain(handler, STOP_CODE))
                    if (!TextUtils.isEmpty(lastResult)) {
                        handler.sendMessage(Message.obtain(handler, FINISH_CODE, lastResult))
                    }
                    recording = false
                    break
                }
            }
            request.onCompleted()

            // Stop recording
            stopRecording()
        }

        streamThread.start()
    }

    private fun stopRecording() {
        if (recorder == null) return
        if (recorder!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            recorder!!.stop()
        }
        if (recorder!!.state == AudioRecord.STATE_INITIALIZED) {
            recorder!!.release()
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            audioManager.abandonAudioFocus(afChangeListener)
        }
    }

    fun destroy() {
        if (recorder != null)
            recorder!!.release()
    }

    companion object {
        private val TAG = VoiceClient::class.java.simpleName
        private val START_CODE = 10001
        private val STOP_CODE = 10002
        private val ERR_CODE = 10003
        private val UPDATE_CODE = 10004
        private val CLIENT_TIME_OUT: Long = 5000
        private val FINISH_CODE = 10005
        private val SAMPLE_RATE = 16000
        private val host = "123.31.18.120"
        private val port = 50051

        var SINGLE_TYPE = true
        var NORMALIZED = true
    }
}