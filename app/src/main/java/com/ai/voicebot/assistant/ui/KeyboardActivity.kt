package com.ai.voicebot.assistant.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.davidmiguel.numberkeyboard.NumberKeyboard
import com.davidmiguel.numberkeyboard.NumberKeyboardListener

class KeyboardActivity : AppCompatActivity(), NumberKeyboardListener {
    private val MIC_REQ_CODE = 1001
    private lateinit var amountEditText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_custom)
        title = "Voice call center"
        amountEditText = findViewById(R.id.amount)
        val numberKeyboard = findViewById<NumberKeyboard>(R.id.numberKeyboard)
        numberKeyboard.setListener(this)
        if (!checkMicPermission()) {
            requestMicPermission(MIC_REQ_CODE)
        }
    }
    protected fun checkMicPermission() = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    protected fun requestMicPermission(requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == MIC_REQ_CODE && resultCode == Activity.RESULT_OK) {
            //todo
        }
    }

    override fun onNumberClicked(number: Int) {
        val newAmount = (phoneNumber * 10.0 + number).toInt()
        if (newAmount <= MAX_ALLOWED_AMOUNT) {
            phoneNumber = newAmount
            showAmount()
        }
    }

    override fun onLeftAuxButtonClicked() {
        if(phoneNumber > 0){
            val intent = Intent(this, MainActivity::class.java)
            KeyboardActivity.isCallin = true
            startActivity(intent);
        }else{
            Toast.makeText(this,"Bạn phải nhập số điện thoại", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        //phoneNumber = 0
    }
    override fun onRightAuxButtonClicked() {
        phoneNumber = (phoneNumber / 10.0).toInt()
        showAmount()
    }

    private fun showAmount() {
        amountEditText.text = "" + phoneNumber.toLong()
    }

    companion object {
        private const val MAX_ALLOWED_AMOUNT = 9999999999
        var  phoneNumber: Int = 0
        var  isCallin : Boolean = true
    }
}
