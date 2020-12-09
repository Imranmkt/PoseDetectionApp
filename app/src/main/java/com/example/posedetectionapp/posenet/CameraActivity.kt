package com.example.posedetectionapp.posenet

import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import com.example.posedetectionapp.R


class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        supportActionBar?.hide()
        setContentView(R.layout.activity_camera)

        savedInstanceState ?: supportFragmentManager.beginTransaction()
                .replace(R.id.container, PoseNetFragment())
                .commit()
    }
}
