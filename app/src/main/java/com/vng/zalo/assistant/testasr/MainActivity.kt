package com.vng.zalo.assistant.testasr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import kotlinx.android.synthetic.main.activity_custom_demo.*

class MainActivity : AppCompatActivity(), RecognitionUICallback {
    private val MIC_REQ_CODE = 1001
    private lateinit var voiceClient: VoiceClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_demo)

        demo_btn_mic.setOnClickListener {
            if (checkMicPermission()) {
                voiceClient.startStreaming()
            }
            else {
                requestMicPermission(MIC_REQ_CODE)
            }
        }

        demo_btn_stop.setOnClickListener {
            voiceClient.stopStreaming()
        }

        demo_chk_normalize.setOnCheckedChangeListener { buttonView, isChecked -> VoiceClient.NORMALIZED = isChecked }
        demo_chk_type.setOnCheckedChangeListener { buttonView, isChecked -> VoiceClient.SINGLE_TYPE = !isChecked }

        voiceClient = VoiceClient(this, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == MIC_REQ_CODE && resultCode == Activity.RESULT_OK) {
            voiceClient.startStreaming()
        }
    }

    override fun onStop() {
        super.onStop()
        voiceClient.stopStreaming()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceClient.destroy()
    }

    protected fun requestMicPermission(requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
    }

    protected fun checkMicPermission() = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // Implemented methods
    override fun onStopRec() {
        demo_btn_mic.visibility = View.VISIBLE
        demo_btn_stop.visibility = View.GONE
        demo_txt_preview.visibility = View.INVISIBLE
    }

    override fun onStartRec() {
        demo_btn_mic.visibility = View.GONE
        demo_btn_stop.visibility = View.VISIBLE
        demo_txt_preview.visibility = View.VISIBLE
        demo_final_text.visibility = View.INVISIBLE
        demo_txt_preview.text = ""
    }

    override fun onUpdateText(text: String) {
        demo_txt_preview.text = text
    }

    override fun onFailed(msg: String) {
        demo_final_text.visibility = View.VISIBLE
        demo_final_text.text = "Error: $msg"
    }

    override fun onFinal(finalText: String) {
        demo_final_text.visibility = View.VISIBLE
        demo_final_text.text = finalText
    }
}
