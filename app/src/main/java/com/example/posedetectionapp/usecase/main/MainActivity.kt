package com.example.posedetectionapp.usecase.main

import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import com.example.posedetectionapp.R
import com.example.posedetectionapp.usecase.BaseActivity
import com.example.posedetectionapp.utils.startActivityNewTask
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BaseActivity() {

    companion object {
        private const val KeyLogout = "clearBackStack"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
    }

    private fun init() {
        if (intent.extras?.getBoolean(KeyLogout) == true) {
            startActivityNewTask(MainActivity::class.java)
            finish()
        }
        imageMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        navigationView.itemIconTintList = null
        val navController = Navigation.findNavController(this, R.id.navHostFragment)
        NavigationUI.setupWithNavController(navigationView, navController)

        navController.addOnDestinationChangedListener { _: NavController, destination: NavDestination, _: Bundle? ->
            run {
                textTitle.text = destination.label
            }
        }
    }
}