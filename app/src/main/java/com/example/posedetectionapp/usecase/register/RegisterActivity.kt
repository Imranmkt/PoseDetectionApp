package com.example.posedetectionapp.usecase.register

import android.os.Bundle
import com.example.posedetectionapp.R
import com.example.posedetectionapp.data.database.UserService
import com.example.posedetectionapp.usecase.BaseActivity
import com.example.posedetectionapp.usecase.login.LoginActivity
import com.example.posedetectionapp.utils.onChange
import com.example.posedetectionapp.utils.startActivity
import kotlinx.android.synthetic.main.activity_register.*
import kotlinx.android.synthetic.main.activity_register.etEmail
import kotlinx.android.synthetic.main.activity_register.etPassword

class RegisterActivity : BaseActivity(), RegisterPresenter.View {

    private lateinit var registerPresenter: RegisterPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        init()
    }

    private fun init() {
        registerPresenter = RegisterPresenter(this, UserService(this))
        btnCreateAccount.setOnClickListener {
            registerPresenter.doRegister()
        }
        etName.onChange { registerPresenter.updateName(it) }
        etEmail.onChange { registerPresenter.updateEmail(it) }
        etAge.onChange { registerPresenter.updateAge(it) }
        etPassword.onChange { registerPresenter.updatePassword(it) }

        goToLogin.setOnClickListener {
            startActivity(LoginActivity::class.java)
        }
    }

    override fun registerSuccess() {
        startActivity(LoginActivity::class.java)
        finish()
    }

    override fun showError(error: String) {
        toast(error)
    }
}
