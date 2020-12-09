package com.example.posedetectionapp.utils.hbLib

import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.posedetectionapp.R
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordService : Service() {

    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mScreenDensity = 0
    private var mResultCode = 0
    private var mResultData: Intent? = null
    private var isVideoHD = false
    private var isAudioEnabled = false
    private var path: String? = null
    private var mMediaProjection: MediaProjection? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    var name: String? = null
    private var audioBitrate = 0
    private var audioSamplingRate = 0
    private var audioSourceAsInt = 0
    private var videoEncoderAsInt = 0
    private var isCustomSettingsEnabled = false
    private var videoFrameRate = 0
    private var videoBitrate = 0
    private var outputFormatAsInt = 0
    private var orientationHint = 0
    private var returnedUri: Uri? = null
    private var mIntent: Intent? = null


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val pauseResumeAction = intent.action
        if (pauseResumeAction != null && pauseResumeAction == "pause") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pauseRecording()
            }
        } else if (pauseResumeAction != null && pauseResumeAction == "resume") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                resumeRecording()
            }
        } else {
            mIntent = intent
            val notificationSmallIcon = intent.getByteArrayExtra("notificationSmallBitmap")
            var notificationTitle = intent.getStringExtra("notificationTitle")
            var notificationDescription = intent.getStringExtra("notificationDescription")
            var notificationButtonText = intent.getStringExtra("notificationButtonText")
            orientationHint = intent.getIntExtra("orientation", 400)
            mResultCode = intent.getIntExtra("code", -1)
            mResultData = intent.getParcelableExtra("data")
            mScreenWidth = intent.getIntExtra("width", 0)
            mScreenHeight = intent.getIntExtra("height", 0)
            if (intent.getStringExtra("mUri") != null) {
                returnedUri = Uri.parse(intent.getStringExtra("mUri"))
            }
            if (mScreenHeight == 0 || mScreenWidth == 0) {
                val hbRecorderCodecInfo = HBRecorderCodecInfo()
                hbRecorderCodecInfo.setContext(this)
                mScreenHeight = hbRecorderCodecInfo.getMaxSupportedHeight()
                mScreenWidth = hbRecorderCodecInfo.getMaxSupportedWidth()
            }
            mScreenDensity = intent.getIntExtra("density", 1)
            isVideoHD = intent.getBooleanExtra("quality", true)
            isAudioEnabled = intent.getBooleanExtra("audio", true)
            path = intent.getStringExtra("path")
            name = intent.getStringExtra("fileName")
            val audioSource = intent.getStringExtra("audioSource")
            val videoEncoder = intent.getStringExtra("videoEncoder")
            videoFrameRate = intent.getIntExtra("videoFrameRate", 30)
            videoBitrate = intent.getIntExtra("videoBitrate", 40000000)
            audioSource?.let { setAudioSourceAsInt(it) }
            videoEncoder?.let { setVideoEncoderAsInt(it) }
            filePath = name
            audioBitrate = intent.getIntExtra("audioBitrate", 128000)
            audioSamplingRate = intent.getIntExtra("audioSamplingRate", 44100)
            val outputFormat = intent.getStringExtra("outputFormat")
            outputFormat?.let { setOutputAsInt(it) }
            isCustomSettingsEnabled = intent.getBooleanExtra("enableCustomSettings", false)

            if (notificationButtonText == null) {
                notificationButtonText = "STOP RECORDING"
            }
            if (audioBitrate == 0) {
                audioBitrate = 128000
            }
            if (audioSamplingRate == 0) {
                audioSamplingRate = 44100
            }
            if (notificationTitle == null || notificationTitle == "") {
                notificationTitle = "Recording your screen"
            }
            if (notificationDescription == null || notificationDescription == "") {
                notificationDescription = "Drag down to stop the recording"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "001"
                val channelName = "RecordChannel"
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
                channel.lightColor = Color.BLUE
                channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
                val notification: Notification
                val myIntent = Intent(this, NotificationReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(this, 0, myIntent, 0)
                val action = Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.presence_video_online),
                        notificationButtonText,
                        pendingIntent).build()
                notification = if (notificationSmallIcon != null) {
                    val bmp = BitmapFactory.decodeByteArray(notificationSmallIcon, 0, notificationSmallIcon.size)
                    Notification.Builder(applicationContext, channelId).setOngoing(true).setSmallIcon(Icon.createWithBitmap(bmp)).setContentTitle(notificationTitle).setContentText(notificationDescription).addAction(action).build()
                } else {
                    Notification.Builder(applicationContext, channelId).setOngoing(true).setSmallIcon(R.drawable.record_icon).setContentTitle(notificationTitle).setContentText(notificationDescription).addAction(action).build()
                }
                startForeground(101, notification)
            } else {
                startForeground(101, Notification())
            }
            if (returnedUri == null) {
                if (path == null) {
                    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString()
                }
            }
            try {
                initRecorder()
            } catch (e: Exception) {
                val receiver = intent.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("errorReason", Log.getStackTraceString(e))
                receiver?.send(Activity.RESULT_OK, bundle)
            }
            try {
                initMediaProjection()
            } catch (e: Exception) {
                val receiver = intent.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("errorReason", Log.getStackTraceString(e))
                receiver?.send(Activity.RESULT_OK, bundle)
            }

            try {
                initVirtualDisplay()
            } catch (e: Exception) {
                val receiver = intent.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("errorReason", Log.getStackTraceString(e))
                receiver?.send(Activity.RESULT_OK, bundle)
            }

            mMediaRecorder!!.setOnErrorListener { _, i, _ ->
                val receiver = intent.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("error", "38")
                bundle.putString("errorReason", i.toString())
                receiver?.send(Activity.RESULT_OK, bundle)
            }

            try {
                mMediaRecorder!!.start()
                val receiver = intent.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("onStart", "111")
                receiver?.send(Activity.RESULT_OK, bundle)
            } catch (e: Exception) {

                val receiver = intent.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("error", "38")
                bundle.putString("errorReason", Log.getStackTraceString(e))
                receiver?.send(Activity.RESULT_OK, bundle)
            }
        }
        return START_STICKY
    }

    private fun pauseRecording() {
        mMediaRecorder!!.pause()
    }

    private fun resumeRecording() {
        mMediaRecorder!!.resume()
    }

    private fun setOutputAsInt(outputFormat: String) {
        outputFormatAsInt = when (outputFormat) {
            "DEFAULT" -> 0
            "THREE_GPP" -> 1
            "MPEG_4" -> 2
            "AMR_NB" -> 3
            "AMR_WB" -> 4
            "AAC_ADTS" -> 6
            "MPEG_2_TS" -> 8
            "WEBM" -> 9
            "OGG" -> 11
            else -> 2
        }
    }

    private fun setVideoEncoderAsInt(encoder: String) {
        when (encoder) {
            "DEFAULT" -> videoEncoderAsInt = 0
            "H263" -> videoEncoderAsInt = 1
            "H264" -> videoEncoderAsInt = 2
            "MPEG_4_SP" -> videoEncoderAsInt = 3
            "VP8" -> videoEncoderAsInt = 4
            "HEVC" -> videoEncoderAsInt = 5
        }
    }

    private fun setAudioSourceAsInt(audioSource: String) {
        when (audioSource) {
            "DEFAULT" -> audioSourceAsInt = 0
            "MIC" -> audioSourceAsInt = 1
            "VOICE_UPLINK" -> audioSourceAsInt = 2
            "VOICE_DOWNLINK" -> audioSourceAsInt = 3
            "VOICE_CALL" -> audioSourceAsInt = 4
            "CAMCODER" -> audioSourceAsInt = 5
            "VOICE_RECOGNITION" -> audioSourceAsInt = 6
            "VOICE_COMMUNICATION" -> audioSourceAsInt = 7
            "REMOTE_SUBMIX" -> audioSourceAsInt = 8
            "UNPROCESSED" -> audioSourceAsInt = 9
            "VOICE_PERFORMANCE" -> audioSourceAsInt = 10
        }
    }

    private fun initMediaProjection() {
        mMediaProjection = (Objects.requireNonNull(getSystemService(MEDIA_PROJECTION_SERVICE)) as MediaProjectionManager).getMediaProjection(mResultCode, mResultData!!)
    }

    @Throws(Exception::class)
    private fun initRecorder() {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        val curTime = formatter.format(curDate).replace(" ", "")
        var videoQuality = "HD"
        if (!isVideoHD) {
            videoQuality = "SD"
        }
        if (name == null) {
            name = videoQuality + curTime
        }
        filePath = "$path/$name.mp4"
        fileName = "$name.mp4"
        mMediaRecorder = MediaRecorder()
        if (isAudioEnabled) {
            mMediaRecorder!!.setAudioSource(audioSourceAsInt)
        }
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(outputFormatAsInt)
        if (orientationHint != 400) {
            mMediaRecorder!!.setOrientationHint(orientationHint)
        }
        if (isAudioEnabled) {
            mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mMediaRecorder!!.setAudioEncodingBitRate(audioBitrate)
            mMediaRecorder!!.setAudioSamplingRate(audioSamplingRate)
        }
        mMediaRecorder!!.setVideoEncoder(videoEncoderAsInt)
        if (returnedUri != null) {
            try {
                val contentResolver = contentResolver
                val inputPFD = contentResolver.openFileDescriptor(returnedUri!!, "rw")?.fileDescriptor
                mMediaRecorder!!.setOutputFile(inputPFD)
            } catch (e: Exception) {
                val receiver = mIntent!!.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
                val bundle = Bundle()
                bundle.putString("errorReason", Log.getStackTraceString(e))
                receiver?.send(Activity.RESULT_OK, bundle)
            }
        } else {
            mMediaRecorder!!.setOutputFile(filePath)
        }
        mMediaRecorder!!.setVideoSize(mScreenWidth, mScreenHeight)
        if (!isCustomSettingsEnabled) {
            if (!isVideoHD) {
                mMediaRecorder!!.setVideoEncodingBitRate(12000000)
                mMediaRecorder!!.setVideoFrameRate(30)
            } else {
                mMediaRecorder!!.setVideoEncodingBitRate(5 * mScreenWidth * mScreenHeight)
                mMediaRecorder!!.setVideoFrameRate(60) //after setVideoSource(), setOutFormat()
            }
        } else {
            mMediaRecorder!!.setVideoEncodingBitRate(videoBitrate)
            mMediaRecorder!!.setVideoFrameRate(videoFrameRate)
        }
        mMediaRecorder!!.prepare()
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun initVirtualDisplay() {
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(TAG, mScreenWidth, mScreenHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder!!.surface, null, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onDestroy() {
        super.onDestroy()
        resetAll()
        callOnComplete()
    }

    private fun callOnComplete() {
        val receiver = mIntent!!.getParcelableExtra<ResultReceiver>(BUNDLED_LISTENER)
        val bundle = Bundle()
        bundle.putString("onComplete", "Uri was passed")
        receiver?.send(Activity.RESULT_OK, bundle)
    }

    private fun resetAll() {
        stopForeground(true)
        if (mVirtualDisplay != null) {
            mVirtualDisplay!!.release()
            mVirtualDisplay = null
        }
        if (mMediaRecorder != null) {
            mMediaRecorder!!.setOnErrorListener(null)
            mMediaProjection!!.stop()
            mMediaRecorder!!.reset()
        }
        if (mMediaProjection != null) {
            mMediaProjection!!.stop()
            mMediaProjection = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        var filePath: String? = null
            private set
        var fileName: String? = null
            private set
        const val BUNDLED_LISTENER = "listener"
    }
}