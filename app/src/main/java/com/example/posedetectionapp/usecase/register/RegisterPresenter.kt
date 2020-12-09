package com.example.posedetectionapp.usecase.register

import com.example.posedetectionapp.data.database.UserService
import com.example.posedetectionapp.data.models.User

class RegisterPresenter(val view: View, private val userService: UserService) {

    private var user: User = User()

    fun updateName(name: String) {
        user.name = name
    }

    fun updateEmail(email: String) {
        user.email = email
    }

    fun updateAge(age: String) {
        if (age.isNotEmpty()) {
            user.age = age.toInt()
        } else {
            user.age = 0
        }
    }

    fun updatePassword(password: String) {
        user.password = password
    }

    private fun showError(error: String) {
        view.showError(error)
    }

    fun doRegister() {
        when {
            (user.name.isEmpty()) -> showError("Please enter name")
            (user.email.isEmpty()) -> showError("Please enter email")
            (user.age <= 0) -> showError("Please enter a valid age")
            (user.password.isEmpty()) -> showError("Please enter password")
            (user.password.length < 6) -> showError("Password must be at least 6 character long.")
            else -> register()
        }
    }

    private fun register() {
        if (userService.createUser(user)) {
            view.registerSuccess()
        } else {
            showError("Could not create account")
        }
    }

    interface View {
        fun registerSuccess()
        fun showError(error: String)
    }
}
