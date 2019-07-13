package com.ai.voicebot.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import com.ai.voicebot.assistant.ui.RecognitionUICallback
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import service.StreamVoiceGrpc
import service.TextReply
import service.VoiceRequest

import javax.net.ssl.SSLException

class VoiceClientBak (private val channel: ManagedChannel) {
    private val asyncStub: StreamVoiceGrpc.StreamVoiceStub

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
                    recognitionUICallback.onUpdateTextAsr(msgIfAny)
                }
                FINISH_CODE -> {
                    if (msgIfAny == "") return
                    recognitionUICallback.onFinal(msgIfAny)
                }
            }
        }
    }

    private var recording = false

    internal var recorder: AudioRecord? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val request: StreamObserver<VoiceRequest>? = null
    internal var minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var lastServerResponseTime: Long = 0
    private var lastResult: String? = null

    @Throws(SSLException::class)
    constructor(context: Context, uiCallback: RecognitionUICallback) : this(
        ManagedChannelBuilder.forAddress(
            host,
            port
        )
            .useTransportSecurity()
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
        header.put(Metadata.Key.of("channels", Metadata.ASCII_STRING_MARSHALLER), "1")
        header.put(Metadata.Key.of("rate", Metadata.ASCII_STRING_MARSHALLER), "16000")
        header.put(Metadata.Key.of("format", Metadata.ASCII_STRING_MARSHALLER), "S16LE")
        header.put(Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER), "kiki")
        //        header.put(Metadata.Key.of("zalo-name", Metadata.ASCII_STRING_MARSHALLER),
        //                ZaloUserFilter.INSTANCE.getRawString(ZaloSDK.Instance.getZaloDisplayname().toLowerCase()));
        header.put(
            Metadata.Key.of("single-sentence", Metadata.ASCII_STRING_MARSHALLER),
            if (SINGLE_TYPE) "True" else "False"
        )
        asyncStub = MetadataUtils.attachHeaders<StreamVoiceGrpc.StreamVoiceStub>(StreamVoiceGrpc.newStub(channel), header)
    }

    fun stopStreaming() {
        if (recording) {
            recording = false
            handler.sendMessage(Message.obtain(handler,
                STOP_CODE
            ))
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

            val responseObserver = object : StreamObserver<TextReply> {
                //Định nghĩa sẽ làm gì với TextReply asr_response trả về:
                override fun onNext(textReply: TextReply) {
                    if (!textReply.hasResult()) return
                    if (!recording) return
                    lastServerResponseTime = System.currentTimeMillis()
                    val resultFinal = textReply.result.final
                    lastResult = if (NORMALIZED)
                        textReply.result.getHypotheses(0).transcriptNormed
                    else
                        textReply.result.getHypotheses(0).transcript

                    handler.sendMessage(Message.obtain(handler,
                        UPDATE_CODE, lastResult))

                    // Only when single
                    if (resultFinal && SINGLE_TYPE) {
                        handler.sendMessage(Message.obtain(handler,
                            FINISH_CODE, lastResult))
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
                        handler.sendMessageDelayed(Message.obtain(handler,
                            FINISH_CODE, ""), 300)
                    } else {
                        // Multi type will not end in onNext so we analyze here
                        if (!SINGLE_TYPE) {
                            handler.sendMessage(Message.obtain(handler,
                                FINISH_CODE, lastResult))
                            recorder!!.stop()
                        }
                    }
                }
            }

            val request = asyncStub.sendVoice(responseObserver)

            handler.sendMessage(Message.obtain(handler,
                START_CODE
            ))

            recorder!!.startRecording()
            lastServerResponseTime = System.currentTimeMillis()
            recording = true

            while (recording) {
                if (recorder!!.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    handler.sendMessage(
                        Message.obtain(
                            handler, ERR_CODE,
                            "Record error"
                        )
                    )
                    handler.sendMessageDelayed(Message.obtain(handler,
                        STOP_CODE
                    ), 300)

                    recording = false
                } else {
                    recorder!!.read(audioBuffer, 0, audioBuffer.size)
                    request.onNext(VoiceRequest.newBuilder().setByteBuff(ByteString.copyFrom(audioBuffer)).build())
                }

                if (System.currentTimeMillis() - lastServerResponseTime > CLIENT_TIME_OUT) {
                    handler.sendMessage(
                        Message.obtain(
                            handler, ERR_CODE,
                            "Server ASR not response!"
                        )
                    )
                    handler.sendMessage(Message.obtain(handler,
                        STOP_CODE
                    ))
                    if (!TextUtils.isEmpty(lastResult)) {
                        handler.sendMessage(Message.obtain(handler,
                            FINISH_CODE, lastResult))
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
        private val TAG = VoiceClientBak::class.java.simpleName
        private val START_CODE = 10001
        private val STOP_CODE = 10002
        private val ERR_CODE = 10003
        private val UPDATE_CODE = 10004
        private val CLIENT_TIME_OUT: Long = 5000
        private val FINISH_CODE = 10005
        private val SAMPLE_RATE = 16000
        private val host = "asr.kiki.laban.vn"
        private val port = 443

        var SINGLE_TYPE = true
        var NORMALIZED = true
    }
}