package com.example.posedetectionapp.usecase

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    protected fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}