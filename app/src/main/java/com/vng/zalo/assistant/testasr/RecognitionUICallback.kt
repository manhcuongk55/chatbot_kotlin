package com.vng.zalo.assistant.testasr

interface RecognitionUICallback {
    fun onStopRec()
    fun onStartRec()
    fun onUpdateText(text: String)
    fun onFailed(msg: String)
    fun onFinal(finalText: String)
}
