package com.example.posedetectionapp.usecase.player

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.posedetectionapp.R


class VideoPlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        init()
    }

    private fun init() {
        val videoView = findViewById<VideoView>(R.id.video_view)
        val videoPath = intent.getParcelableExtra<Uri>("VideoUri");
        videoView.setVideoURI(videoPath)
        videoView.start()
        val mediaController = MediaController(this)
        videoView.setMediaController(mediaController)
        mediaController.setAnchorView(videoView)
    }
}