package com.example.posedetectionapp.utils

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun Activity.startActivity(clazz: Class<*>) {
    val intent = Intent(this, clazz)
    startActivity(intent)
}

fun Activity.startActivityNewTask(clazz: Class<*>) {
    val intent = Intent(this, clazz)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}


fun EditText.onChange(cb: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            cb(s.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}