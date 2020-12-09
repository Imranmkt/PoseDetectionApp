package com.example.posedetectionapp.utils.hbLib

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.annotation.DrawableRes
import com.example.posedetectionapp.utils.MyListener
import java.io.ByteArrayOutputStream
import java.io.File

class HBRecorder(var context: Context, listener: HBRecorderListener) : MyListener {

    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mScreenDensity = 0
    private var resultCode = 0
    private var isAudioEnabled = true
    private var isVideoHDEnabled = true
    private var activity: Activity? = null
    private var outputPath: String? = null
    var fileName: String = ""
    private var notificationTitle: String? = null
    private var notificationDescription: String? = null
    private var notificationButtonText: String? = null
    private var audioBitrate = 0
    private var audioSamplingRate = 0
    private var observer: FileObserver? = null
    private val hbRecorderListener: HBRecorderListener
    private lateinit var byteArray: ByteArray
    private var audioSource = "MIC"
    private var videoEncoder = "DEFAULT"
    private var enableCustomSettings = false
    private var videoFrameRate = 30
    private var videoBitrate = 40000000
    private var outputFormat = "DEFAULT"
    private var orientation = 0
    var wasOnErrorCalled = false
    private var service: Intent? = null


    fun setOutputPath(path: String?) {
        outputPath = path
    }

    var mUri: Uri? = null
    var mWasUriSet = false


    fun wasUriSet(): Boolean {
        return mWasUriSet
    }

    fun setAudioBitrate(audioBitrate: Int) {
        this.audioBitrate = audioBitrate
    }

    fun setAudioSamplingRate(audioSamplingRate: Int) {
        this.audioSamplingRate = audioSamplingRate
    }

    fun isAudioEnabled(bool: Boolean) {
        isAudioEnabled = bool
    }


    fun recordHDVideo(bool: Boolean) {
        isVideoHDEnabled = bool
    }

    private fun setScreenDensity() {
        val metrics = Resources.getSystem().displayMetrics
        mScreenDensity = metrics.densityDpi
    }

    val filePath: String?
        get() = ScreenRecordService.filePath

    fun startScreenRecording(data: Intent, resultCode: Int, activity: Activity?) {
        this.resultCode = resultCode
        this.activity = activity
        startService(data)
    }

    fun stopScreenRecording() {
        val service = Intent(context, ScreenRecordService::class.java)
        context.stopService(service)
    }


    val isBusyRecording: Boolean
        get() {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (ScreenRecordService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

    fun setNotificationSmallIcon(bytes: ByteArray) {
        byteArray = bytes
    }

    fun setNotificationTitle(Title: String?) {
        notificationTitle = Title
    }


    fun setNotificationDescription(Description: String?) {
        notificationDescription = Description
    }

    private fun startService(data: Intent) {
        try {
            if (!mWasUriSet) {
                observer = if (outputPath != null && outputPath?.isNotEmpty() == true) {
                    val file = File(outputPath!!)
                    val parent = file.parent
                    FileObserver(parent!!, activity!!, this@HBRecorder)
                } else {
                    FileObserver(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString(), activity!!, this@HBRecorder)
                }
                observer!!.startWatching()
            }
            service = Intent(context, ScreenRecordService::class.java)
            if (mWasUriSet) {
                service!!.putExtra("mUri", mUri.toString())
            }
            service!!.putExtra("code", resultCode)
            service!!.putExtra("data", data)
            service!!.putExtra("audio", isAudioEnabled)
            service!!.putExtra("width", mScreenWidth)
            service!!.putExtra("height", mScreenHeight)
            service!!.putExtra("density", mScreenDensity)
            service!!.putExtra("quality", isVideoHDEnabled)
            service!!.putExtra("path", outputPath)
            service!!.putExtra("fileName", fileName)
            service!!.putExtra("orientation", orientation)
            service!!.putExtra("audioBitrate", audioBitrate)
            service!!.putExtra("audioSamplingRate", audioSamplingRate)
            service!!.putExtra("notificationSmallBitmap", byteArray)
            service!!.putExtra("notificationTitle", notificationTitle)
            service!!.putExtra("notificationDescription", notificationDescription)
            service!!.putExtra("notificationButtonText", notificationButtonText)
            service!!.putExtra("enableCustomSettings", enableCustomSettings)
            service!!.putExtra("audioSource", audioSource)
            service!!.putExtra("videoEncoder", videoEncoder)
            service!!.putExtra("videoFrameRate", videoFrameRate)
            service!!.putExtra("videoBitrate", videoBitrate)
            service!!.putExtra("outputFormat", outputFormat)
            service!!.putExtra(ScreenRecordService.BUNDLED_LISTENER, object : ResultReceiver(Handler()) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                    super.onReceiveResult(resultCode, resultData)
                    if (resultCode == Activity.RESULT_OK) {
                        val errorListener = resultData.getString("errorReason")
                        val onComplete = resultData.getString("onComplete")
                        val onStart = resultData.getString("onStart")
                        when {
                            errorListener != null -> {
                                if (!mWasUriSet) {
                                    observer!!.stopWatching()
                                }
                                wasOnErrorCalled = true
                                hbRecorderListener.onErrorHBRecorder(100, errorListener)
                                try {
                                    val mservice = Intent(context, ScreenRecordService::class.java)
                                    context.stopService(mservice)
                                } catch (e: Exception) {

                                }
                            }
                            onComplete != null -> {

                                if (mWasUriSet && !wasOnErrorCalled) {
                                    hbRecorderListener.onCompleteHBRecorder()
                                }
                                wasOnErrorCalled = false
                            }
                            onStart != null -> {
                                hbRecorderListener.onStartHBRecorder()
                            }
                        }
                    }
                }
            })
            context.startService(service)
        } catch (e: Exception) {
            hbRecorderListener.onErrorHBRecorder(0, Log.getStackTraceString(e))
        }
    }


    override fun callback() {
        observer!!.stopWatching()
        hbRecorderListener.onCompleteHBRecorder()
    }

    init {
        this.context = context.applicationContext
        hbRecorderListener = listener
        setScreenDensity()
    }
}