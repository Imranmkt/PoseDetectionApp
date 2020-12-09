package com.example.posedetectionapp.utils.hbLib

import android.content.Context
import android.content.res.Configuration
import android.media.CamcorderProfile
import android.util.DisplayMetrics
import android.view.WindowManager

class HBRecorderCodecInfo {

    fun getMaxSupportedWidth(): Int = getRecordingInfo().width
    fun getMaxSupportedHeight(): Int = getRecordingInfo().height

    private fun getRecordingInfo(): RecordingInfo {
        val displayMetrics = DisplayMetrics()
        val wm = context!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(displayMetrics)
        val displayWidth = displayMetrics.widthPixels
        val displayHeight = displayMetrics.heightPixels
        val displayDensity = displayMetrics.densityDpi
        val configuration = context!!.resources.configuration
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
        val cameraWidth = camcorderProfile?.videoFrameWidth ?: -1
        val cameraHeight = camcorderProfile?.videoFrameHeight ?: -1
        val cameraFrameRate = camcorderProfile?.videoFrameRate ?: 30
        return calculateRecordingInfo(displayWidth, displayHeight, displayDensity, isLandscape,
                cameraWidth, cameraHeight, cameraFrameRate, 100)
    }

    private var context: Context? = null
    fun setContext(c: Context?) {
        context = c
    }

    class RecordingInfo(val width: Int, val height: Int, val frameRate: Int, val density: Int)

    companion object {
        fun calculateRecordingInfo(width: Int, height: Int, displayDensity: Int, isLandscapeDevice: Boolean, cameraWidth: Int, cameraHeight: Int, cameraFrameRate: Int, sizePercentage: Int): RecordingInfo {

            var displayWidth = width
            var displayHeight = height
            displayWidth = displayWidth * sizePercentage / 100
            displayHeight = displayHeight * sizePercentage / 100
            if (cameraWidth == -1 && cameraHeight == -1) {

                return RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity)
            }
            var frameWidth = if (isLandscapeDevice) cameraWidth else cameraHeight
            var frameHeight = if (isLandscapeDevice) cameraHeight else cameraWidth
            if (frameWidth >= displayWidth && frameHeight >= displayHeight) {

                return RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity)
            }

            if (isLandscapeDevice) {
                frameWidth = displayWidth * frameHeight / displayHeight
            } else {
                frameHeight = displayHeight * frameWidth / displayWidth
            }
            return RecordingInfo(frameWidth, frameHeight, cameraFrameRate, displayDensity)
        }
    }
}