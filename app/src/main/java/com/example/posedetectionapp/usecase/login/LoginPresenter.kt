package com.example.posedetectionapp.usecase.login

import com.example.posedetectionapp.data.database.UserService
import com.example.posedetectionapp.data.models.User

class LoginPresenter(val view: View, private val userService: UserService) {

    private var user: User = User()

    fun updateEmail(email: String) {
        user.email = email
    }

    fun updatePassword(password: String) {
        user.password = password
    }

    private fun showError(error: String) {
        view.showError(error)
    }

    fun doLogin() {
        when {
            (user.email.isEmpty()) -> showError("Please enter email")
            (user.password.isEmpty()) -> showError("Please enter password")
            else -> login()
        }
    }

    private fun login() {
        if (userService.loginUser(user)) {
            view.loginSuccess()
        } else {
            showError("Could not login. Make sure your login credentials are correct.")
        }
    }

    interface View {
        fun loginSuccess()
        fun showError(error: String)
    }
}
