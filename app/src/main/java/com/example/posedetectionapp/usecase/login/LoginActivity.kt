package com.example.posedetectionapp.usecase.login

import android.os.Bundle
import com.example.posedetectionapp.R
import com.example.posedetectionapp.data.database.UserService
import com.example.posedetectionapp.usecase.BaseActivity
import com.example.posedetectionapp.usecase.main.MainActivity
import com.example.posedetectionapp.usecase.register.RegisterActivity
import com.example.posedetectionapp.utils.onChange
import com.example.posedetectionapp.utils.startActivity
import com.example.posedetectionapp.utils.startActivityNewTask
import kotlinx.android.synthetic.main.activity_login.*


class LoginActivity : BaseActivity(), LoginPresenter.View {

    private lateinit var loginPresenter: LoginPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        init()
    }

    private fun init() {
        loginPresenter = LoginPresenter(this, UserService(this))

        btnLoginAccount.setOnClickListener {
            loginPresenter.doLogin()
        }
        etEmail.onChange { loginPresenter.updateEmail(it) }

        etPassword.onChange { loginPresenter.updatePassword(it) }

        gotoRegister.setOnClickListener {
            startActivity(RegisterActivity::class.java)
        }
    }


    override fun loginSuccess() {
        startActivityNewTask(MainActivity::class.java)
        finish()
    }

    override fun showError(error: String) {
        toast(error)
    }
}
