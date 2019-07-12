package com.vng.zalo.assistant.testasr

import java.util.*

interface RecognitionUICallback {
    fun onStopRec()
    fun onStartRec()
    fun onUpdateText(text: String)
    fun onUpdateAudio(url: String)
    fun onFailed(msg: String)
    fun onFinal(finalText: String)
}
