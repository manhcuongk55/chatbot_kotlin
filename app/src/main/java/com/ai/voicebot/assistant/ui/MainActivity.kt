package com.ai.voicebot.assistant.ui

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ai.voicebot.assistant.service.VoiceClient
import kotlinx.android.synthetic.main.activity_dialog.*
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate


class MainActivity : AppCompatActivity(), RecognitionUICallback {

    private lateinit var voiceClient: VoiceClient
    private var count: Long = 0
    val timer = Timer("schedule", true)
    lateinit var mp :MediaPlayer
    private val handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                1 -> {
                    waveview.speechStarted()
                }
                2 -> {
                    voiceClient.startStreaming()
                    timer.scheduleAtFixedRate(1000, 1000) {
                        runOnUiThread {
                            count++
                            tv_calling.text = java.lang.String.format("%2d:%2d", count / 60, count % 60)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog)
        tv_number.text = "" + KeyboardActivity.phoneNumber
        voiceClient = VoiceClient(this, this)
        try {
            if(KeyboardActivity.isCallin){
                demo_btn_mic.visibility = View.GONE
                view_padding.visibility = View.GONE
                mp = MediaPlayer.create(this,R.raw.beep1)
                mp.setOnPreparedListener{
                    mp.start()
                }
                val timer = object : CountDownTimer(2000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        tv_calling.text = "Calling ........."
                    }

                    override fun onFinish() {
                        handler.sendMessage(Message.obtain(handler, 2))
                    }
                }
                timer.start()
            }else{
                demo_btn_mic.visibility = View.VISIBLE
                view_padding.visibility = View.VISIBLE
                mp = MediaPlayer.create(this,R.raw.call_out)
                mp.setOnPreparedListener {
                    mp.start()
                }
                demo_btn_mic.setOnClickListener {
                    demo_btn_stop.visibility = View.VISIBLE
                    view_padding.visibility = View.GONE
                    demo_btn_mic.visibility = View.GONE
                    mp.release()
                    voiceClient.startStreaming()
                    timer.scheduleAtFixedRate(1000, 1000) {
                        runOnUiThread {
                            count++
                            tv_calling.text = java.lang.String.format("%2d:%2d", count / 60, count % 60)
                        }
                    }
                }
            }
            demo_btn_stop.setOnClickListener {
                try {
                    if(KeyboardActivity.isCallin){
                        voiceClient.stopStreaming()
                        finish()
                    } else {
                        mp!!.release()
                        finish()
                    }
                }catch (e: Exception){
                    e.stackTrace
                }
            }
        }catch (e:Exception){
           e.stackTrace
        }
       /* when (KeyboardActivity.phoneNumber) {
            DUOC_CALL_IN -> {
                voiceClient.callCenter = "" + DUOC_CALL_IN
            }
            DUOC_CALL_OUT -> {
                voiceClient.callCenter = "" + DUOC_CALL_OUT
            }
            PIZZA_CALL_IN -> {
                voiceClient.callCenter = "" + PIZZA_CALL_IN
            }
            PIZZA_CALL_OUT -> {
                voiceClient.callCenter = "" + PIZZA_CALL_OUT
            }

        }*/
        voiceClient.callCenter = "" + KeyboardActivity.phoneNumber

    }


    override fun onStop() {
        super.onStop()
        voiceClient.stopStreaming()
        waveview.speechEnded()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceClient.destroy()
    }


    // Implemented methods
    override fun onStopRec() {
        waveview.speechPaused()
    }

    override fun onStartRec() {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        waveview.initialize(dm)
        waveview.speechStarted()
    }

    override fun onUpdateTextAsr(text: String) {
        tv_asr.text = "Client: " + text
    }

    override fun onUpdateTextResponse(text: String) {
        tv_response.text = "Bot: " + text
        tv_response.visibility = View.VISIBLE
    }

    override fun onUpdateAudio(url: String) {
        Log.d(TAG, url);
        Log.d(TAG, "speaking true")
        try {
            waveview.speechPaused()
            voiceClient.speaking = true
            mp = MediaPlayer()
            mp.setDataSource(url)
            mp.prepare()
            mp.start()
            Thread {
                try {
                    while (mp!!.isPlaying()) {
                        Thread.sleep(10)
                    }
                }catch (e: java.lang.Exception){
                    e.stackTrace
                }
                voiceClient.speaking = false
                handler.sendMessage(Message.obtain(handler, 1))
                runOnUiThread {
                    tv_asr.text = "Client: "
                }
            }.start()

            Log.d(TAG, "speaking false")
        } catch (e: Exception) {
            e.stackTrace
        }
    }

    override fun onFailed(msg: String) {
        tv_response.visibility = View.VISIBLE
        tv_calling.visibility = View.INVISIBLE
        // tv_response.text = "Error: $msg"
    }

    override fun onFinal(finalText: String) {
        tv_response.visibility = View.VISIBLE
        tv_response.text = finalText
    }

    override fun onEndCall(mess: String) {
        try {
            if (mess.isEmpty()) {
                Toast.makeText(this, "Cuộc gọi kết thúc", Toast.LENGTH_LONG).show()

            } else {
                Toast.makeText(this, mess, Toast.LENGTH_LONG).show()
            }
            finish()
            mp!!.release()
        }catch (e: Exception){
            e.stackTrace
        }

    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private val DUOC_CALL_IN = 18001111
        private val DUOC_CALL_OUT = 18002222
        private val PIZZA_CALL_IN = 18003333
        private val PIZZA_CALL_OUT = 18004444
    }
}
