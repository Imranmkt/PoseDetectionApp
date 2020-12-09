package com.example.posedetectionapp.usecase.welcome

import android.os.Bundle
import com.example.posedetectionapp.R
import com.example.posedetectionapp.usecase.BaseActivity
import com.example.posedetectionapp.usecase.login.LoginActivity
import com.example.posedetectionapp.usecase.register.RegisterActivity
import com.example.posedetectionapp.utils.startActivity
import kotlinx.android.synthetic.main.activity_welcome.*

class WelcomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        init()
    }

    private fun init() {
        btnLoginActivity.setOnClickListener {
            startActivity(LoginActivity::class.java)
        }
        btnCreateAccountActivity.setOnClickListener {
            startActivity(RegisterActivity::class.java)
        }
    }
}
