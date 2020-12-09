package com.example.posedetectionapp.utils.hbLib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, ScreenRecordService::class.java)
        context.stopService(service)
    }
}