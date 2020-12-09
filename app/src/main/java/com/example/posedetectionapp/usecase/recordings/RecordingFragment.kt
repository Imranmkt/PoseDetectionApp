package com.example.posedetectionapp.usecase.recordings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.posedetectionapp.R
import com.example.posedetectionapp.posenet.CameraActivity
import com.example.posedetectionapp.utils.hbLib.HBRecorder
import com.example.posedetectionapp.utils.hbLib.HBRecorderListener
import kotlinx.android.synthetic.main.fragment_recording.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("ObsoleteSdkInt")
class RecordingFragment : Fragment(), HBRecorderListener {

    private val SCREEN_RECORD_REQUEST_CODE = 777
    private var hasPermissions = false
    private lateinit var sharedPreferences: SharedPreferences
    private var hbRecorder: HBRecorder? = null
    var wasHDSelected = true
    var isAudioEnabled = true
    private var permissions = arrayOf(
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.RECORD_AUDIO"
    )
    var ALL_PERMISSIONS = 101

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_recording, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        button_start!!.setOnClickListener {
            if (hbRecorder!!.isBusyRecording) {
                hbRecorder!!.stopScreenRecording()
                button_start!!.setText(R.string.start_recording)
            } else {
                recordingConfirmationDialog()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Init HBRecorder
            hbRecorder = HBRecorder(requireContext(), this)
            if (hbRecorder!!.isBusyRecording) {
                button_start!!.setText(R.string.stop_recording)
            }
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

    }

    private fun performRecording() {
        if (hasPermissions) {
            if (hbRecorder!!.isBusyRecording) {
                hbRecorder!!.stopScreenRecording()
                button_start!!.setText(R.string.start_recording)
            } else {
                startRecordingScreen()
            }
        } else {
            showLongToast("Please accept all permissions")
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun refreshGalleryFile() {
        MediaScannerConnection.scanFile(activity, arrayOf(hbRecorder!!.filePath), null
        ) { path, uri ->
            Log.i("ExternalStorage", "Scanned $path:")
            Log.i("ExternalStorage", "-> uri=$uri")
        }
    }


    private fun updateGalleryUri() {
        contentValues!!.clear()
        contentValues!!.put(MediaStore.Video.Media.IS_PENDING, 0)
        activity?.contentResolver?.update(mUri!!, contentValues, null, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun startRecordingScreen() {
        quickSettings()
        val mediaProjectionManager = activity?.getSystemService(AppCompatActivity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE)

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun quickSettings() {
        hbRecorder!!.setAudioBitrate(128000)
        hbRecorder!!.setAudioSamplingRate(44100)
        wasHDSelected = sharedPreferences.getString("record_quality", "HD").equals("HD")
        isAudioEnabled = sharedPreferences.getBoolean("record_audio", true)
        hbRecorder!!.recordHDVideo(wasHDSelected)
        hbRecorder!!.isAudioEnabled(isAudioEnabled)
        hbRecorder!!.setNotificationSmallIcon(drawable2ByteArray()!!)
        hbRecorder!!.setNotificationTitle("Recording your screen")
        hbRecorder!!.setNotificationDescription("Drag down to stop the recording")
    }

    //Handle permissions
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        when (requestCode) {
            ALL_PERMISSIONS -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    hasPermissions = true
                    performRecording()
                }
            } else {
                hasPermissions = false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    setOutputPath()
                    hbRecorder!!.startScreenRecording(data!!, resultCode, requireActivity())
                    button_start!!.setText(R.string.stop_recording)
                    val intent = Intent(activity, CameraActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    var resolver: ContentResolver? = null
    var contentValues: ContentValues? = null
    var mUri: Uri? = null

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun setOutputPath() {
        saveInInternalStore()
        saveInGallery()
    }

    private fun saveInGallery() {
        val filename = generateFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver = activity?.contentResolver
            contentValues = ContentValues()
            contentValues!!.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "PoseDetection")
            contentValues!!.put(MediaStore.Video.Media.TITLE, filename)
            contentValues!!.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            contentValues!!.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            mUri = resolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

            hbRecorder!!.fileName = filename
            hbRecorder?.mUri = mUri
        } else {

            createFolderInGallery()

            hbRecorder!!.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString() + "/PoseDetection")
        }
    }

    private fun createFolderInInternalStorage() {
        val f1 = File(activity?.filesDir, "PoseDetection")
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created")
            }
        }
    }

    private fun saveInInternalStore() {
        createFolderInInternalStorage()
        hbRecorder!!.setOutputPath("${activity?.filesDir}/PoseDetection")
    }

    private fun createFolderInGallery() {
        val f1 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "PoseDetection")
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created")
            }
        }
    }

    private fun generateFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        return formatter.format(curDate).replace(" ", "")
    }

    private fun showLongToast(msg: String) {
        Toast.makeText(activity?.applicationContext, msg, Toast.LENGTH_LONG).show()
    }

    private fun drawable2ByteArray(): ByteArray? {
        val icon = BitmapFactory.decodeResource(resources, R.drawable.record_icon)
        val stream = ByteArrayOutputStream()
        icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    override fun onStartHBRecorder() {
        Log.e("HBRecorder", "HBRecorderOnStart called")
    }

    override fun onCompleteHBRecorder() {
        button_start!!.setText(R.string.start_recording)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (hbRecorder!!.wasUriSet()) {
                updateGalleryUri()
            } else {
                refreshGalleryFile()
            }
        }
    }

    override fun onErrorHBRecorder(errorCode: Int, reason: String?) {
        if (errorCode == 38) {
            showLongToast("Some settings are not supported by your device")
        } else {
            showLongToast("HBRecorderOnError - See Log")
            Log.e("HBRecorderOnError", reason.toString())
        }
        button_start!!.setText(R.string.start_recording)
    }


    private fun recordingConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.DialogTheme)
        builder.setTitle("Record Pose")
                .setMessage("Are you ready to record pose video?")
                .setPositiveButton("Yes") { dialog, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        requestPermissions(permissions, ALL_PERMISSIONS)
                    }
                    dialog.dismiss()
                }.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
        builder.create().show()
    }
}