package com.ai.voicebot.assistant.ui

interface RecognitionUICallback {
    fun onStopRec()
    fun onStartRec()
    fun onUpdateTextAsr(text: String)
    fun onUpdateAudio(url: String)
    fun onUpdateTextResponse(text: String)
    fun onFailed(msg: String)
    fun onFinal(finalText: String)
    fun onEndCall(mess: String)
}
