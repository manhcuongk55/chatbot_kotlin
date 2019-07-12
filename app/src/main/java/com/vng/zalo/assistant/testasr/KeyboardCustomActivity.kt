package com.vng.zalo.assistant.testasr

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast

import com.davidmiguel.numberkeyboard.NumberKeyboard
import com.davidmiguel.numberkeyboard.NumberKeyboardListener

import java.text.NumberFormat

class KeyboardCustomActivity : AppCompatActivity(), NumberKeyboardListener {

    private lateinit var amountEditText: TextView
    private var amount: Int = 0
    private val nf = NumberFormat.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_custom)
        title = "Voice call center"
        amountEditText = findViewById(R.id.amount)
        val numberKeyboard = findViewById<NumberKeyboard>(R.id.numberKeyboard)
        numberKeyboard.setListener(this)
    }

    override fun onNumberClicked(number: Int) {
        val newAmount = (amount * 10.0 + number).toInt()
        if (newAmount <= MAX_ALLOWED_AMOUNT) {
            amount = newAmount
            showAmount()
        }
    }

    override fun onLeftAuxButtonClicked() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent);
    }

    override fun onRightAuxButtonClicked() {
        amount = (amount / 10.0).toInt()
        showAmount()
    }

    private fun showAmount() {
        amountEditText.text = nf.format(amount.toLong())
    }

    companion object {
        private const val MAX_ALLOWED_AMOUNT = 99999
    }
}
