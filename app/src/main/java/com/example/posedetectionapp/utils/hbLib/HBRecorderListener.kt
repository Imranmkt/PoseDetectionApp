package com.example.posedetectionapp.utils.hbLib

interface HBRecorderListener {
    fun onStartHBRecorder()
    fun onCompleteHBRecorder()
    fun onErrorHBRecorder(errorCode: Int, reason: String?)
}