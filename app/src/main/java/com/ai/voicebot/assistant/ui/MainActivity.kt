package com.ai.voicebot.assistant.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ai.voicebot.assistant.service.VoiceClient
import kotlinx.android.synthetic.main.activity_dialog.*
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import android.util.DisplayMetrics



class MainActivity : AppCompatActivity(), RecognitionUICallback {

    private lateinit var voiceClient: VoiceClient
    private var count: Long = 0
    val timer = Timer("schedule", true)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog)
        tv_number.text = "" + KeyboardActivity.amount
        voiceClient = VoiceClient(this, this)
        val timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tv_calling.text = "Calling ........."
            }

            override fun onFinish() {
                voiceClient.startStreaming()
                timer.scheduleAtFixedRate(1000, 1000) {
                    count++
                    tv_calling.text = java.lang.String.format("%2d:%2d",count / 60,count % 60)
                }
            }
        }
        timer.start()
        demo_btn_stop.setOnClickListener {
            voiceClient.stopStreaming()
            finish()
        }

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
    }

    override fun onUpdateTextAsr(text: String) {
        tv_asr.text = "Client: " + text
    }

    override fun onUpdateTextResponse(text: String) {
        tv_response.text = "Bot: " + text
        tv_response.visibility = View.VISIBLE
    }

    override fun onUpdateAudio(url: String) {
        Log.d("onUpdateAudio", url);
        Log.d("quyendb", "speaking true")
        waveview.speechStarted()
        voiceClient.speaking = true
        val mp = MediaPlayer()
        try {
            mp.setDataSource(url)
            mp.prepare()
            mp.start()
            Thread {
                while (mp.isPlaying()){
                    Thread.sleep(10)
                }
                voiceClient.speaking = false
                tv_asr.text = "Client: "
            }.start()
            Log.d("quyendb", "speaking false")
            waveview.speechPaused()
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
    override fun onEndCall() {
        finish()
    }
}
