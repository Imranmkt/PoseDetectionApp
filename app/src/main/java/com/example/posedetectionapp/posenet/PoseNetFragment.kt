package com.example.posedetectionapp.posenet

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.posedetectionapp.R
import com.example.posedetectionapp.posenet.lib.BodyPart
import com.example.posedetectionapp.posenet.lib.Person
import com.example.posedetectionapp.posenet.lib.PoseNet
import kotlinx.android.synthetic.main.activity_posenet.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PoseNetFragment : Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {

    private val bodyJoints = listOf(
            Pair(BodyPart.LEFT_WRIST, BodyPart.LEFT_ELBOW),
            Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_SHOULDER),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
            Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
            Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
            Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
            Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_SHOULDER),
            Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
            Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
            Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
            Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
    )

    private var recording: Boolean = false
    private var screenDensity: Int = 0
    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjectionCallback: MediaProjectionCallback? = null
    private var mediaRecorder: MediaRecorder? = null

    private var videoUri: String = ""
    private val minConfidence = 0.5
    private val circleRadius = 8.0f
    private var paint = Paint()
    private val PREVIEW_WIDTH = 640
    private val PREVIEW_HEIGHT = 480

    private lateinit var poseNet: PoseNet
    private var cameraId: String? = null
    private var surfaceView: SurfaceView? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraDevice: CameraDevice? = null
    private var previewSize: Size? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var frameCounter = 0
    private lateinit var rgbBytes: IntArray
    private var yuvBytes = arrayOfNulls<ByteArray>(3)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var imageReader: ImageReader? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null
    private val cameraOpenCloseLock = Semaphore(1)
    private var flashSupported = false
    private var sensorOrientation: Int? = null
    private var surfaceHolder: SurfaceHolder? = null

    private fun usePixelCopy(videoView: SurfaceView, callback: (Bitmap?) -> Unit) {
        val bitmap: Bitmap = Bitmap.createBitmap(
                videoView.width,
                videoView.height,
                Bitmap.Config.ARGB_8888)
        try {
            val handlerThread = HandlerThread("PixelCopier");
            handlerThread.start()
            PixelCopy.request(videoView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    callback(bitmap)
                }
                handlerThread.quitSafely()
            },
                    Handler(handlerThread.looper)
            )
        } catch (e: IllegalArgumentException) {
            callback(null)
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun processBitmap(context: Context, bitmap: Bitmap?) {
        if (bitmap == null) {
            showToast("Failed to save image.")
            return
        }
        val fileName = "" + System.currentTimeMillis() + ".jpg";
        val relativeLocation = Environment.DIRECTORY_DCIM + File.separator + "PoseDetectionApp";
        Environment.getExternalStoragePublicDirectory(relativeLocation).mkdir();
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "" + fileName)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
        val resolver = context.contentResolver
        var stream: OutputStream? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                val uri = resolver.insert(contentUri, contentValues)
                        ?: throw IOException("Failed to create new MediaStore record.")
                stream = resolver.openOutputStream(uri)
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + File.separator + "PoseDetectionApp"

                val file = File(imagesDir)

                if (!file.exists()) {
                    file.mkdir()
                }

                val image = File(imagesDir, fileName)
                stream = FileOutputStream(image)
            }

            if (stream == null) {
                throw IOException("Failed to get output stream.")
            }

            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                showToast("Image saved to gallery.")
            } else {
                throw IOException("Failed to save bitmap.")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            showToast("Failed to save image.")
        } finally {
            stream?.close()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@PoseNetFragment.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@PoseNetFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@PoseNetFragment.activity?.finish()
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
        ) {
        }
    }

    inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (recording) {
                mediaRecorder!!.stop()
                mediaRecorder!!.reset()
                recording = false
            }

            mediaProjection = null
            stopScreenRecord()
        }
    }

    private fun stopScreenRecord() {
        if (virtualDisplay == null) {
            return
        }
        virtualDisplay!!.release()
        destroyMediaProjection()
        showToast("Recording Stopped")
    }

    private fun destroyMediaProjection() {
        if (mediaProjection != null) {
            mediaProjection!!.unregisterCallback(mediaProjectionCallback)
            mediaProjection!!.stop()
            mediaProjection = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE) {
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            return
        }
        mediaProjectionCallback = MediaProjectionCallback()
        mediaProjection = data?.let { projectionManager?.getMediaProjection(resultCode, it) }
        mediaProjection?.registerCallback(mediaProjectionCallback, null)
        virtualDisplay = mediaProjection!!.createVirtualDisplay("PosenetActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface, null, null
        )
        mediaRecorder?.start()
    }

    private fun initRecorder() {
        try {
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            videoUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString() + StringBuilder("/")
                    .append("imran")
                    .append(SimpleDateFormat("dd-mm-yyyy-hh_mm_ss", Locale.getDefault()).format(Date()))
                    .append(".mp4")
                    .toString()
            mediaRecorder?.setOutputFile(videoUri)
            mediaRecorder?.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder?.setVideoEncodingBitRate(512 * 1000)
            mediaRecorder?.setVideoFrameRate(30)
            val windowManager: WindowManager = activity?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS.get(rotation + 90)
            mediaRecorder?.setOrientationHint(orientation)
            mediaRecorder?.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }


    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.activity_posenet, container, false)

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        surfaceView = view.findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView!!.holder

        val metrics = DisplayMetrics()
        val windowManager: WindowManager = activity?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        screenDensity = metrics.densityDpi

        mediaRecorder = MediaRecorder()

        projectionManager = activity?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager


        DISPLAY_HEIGHT = metrics.heightPixels
        DISPLAY_WIDTH = metrics.widthPixels
        initRecorder()

        captureButton.setOnClickListener {
            usePixelCopy(surfaceView!!) { bitmap: Bitmap? ->
                processBitmap(requireContext(), bitmap)
            }
        }

        backButton.setOnClickListener {
            activity?.onBackPressed()
        }
    }


    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    override fun onStart() {
        super.onStart()
        openCamera()
        poseNet = PoseNet(requireContext())
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        poseNet.close()
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted(grantResults)) {
                ErrorDialog.newInstance(getString(R.string.tfe_pn_request_permission))
                        .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray) = grantResults.all {
        it == PackageManager.PERMISSION_GRANTED
    }

    private fun setUpCameraOutputs() {
        val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                previewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

                imageReader = ImageReader.newInstance(
                        PREVIEW_WIDTH, PREVIEW_HEIGHT,
                        ImageFormat.YUV_420_888, /*maxImages*/ 2
                )

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                previewHeight = previewSize!!.height
                previewWidth = previewSize!!.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                flashSupported =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
        } catch (e: NullPointerException) {
            ErrorDialog.newInstance(getString(R.string.tfe_pn_camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        }
    }

    private fun openCamera() {
        val permissionCamera = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        if (permissionCamera != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        }
        val permissionStorage = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
        }

        setUpCameraOutputs()
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        if (captureSession == null) return
        try {
            cameraOpenCloseLock.acquire()
            captureSession!!.close()
            captureSession = null
            cameraDevice!!.close()
            cameraDevice = null
            imageReader!!.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("imageAvailableListener").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }


    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i]!!)
        }
    }

    private var imageAvailableListener = object : OnImageAvailableListener {
        override fun onImageAvailable(imageReader: ImageReader) {
            if (previewWidth == 0 || previewHeight == 0) return
            val image = imageReader.acquireLatestImage() ?: return
            fillBytes(image.planes, yuvBytes)
            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    /*yRowStride=*/ image.planes[0].rowStride,
                    /*uvRowStride=*/ image.planes[1].rowStride,
                    /*uvPixelStride=*/ image.planes[1].pixelStride,
                    rgbBytes
            )
            val imageBitmap = Bitmap.createBitmap(
                    rgbBytes, previewWidth, previewHeight,
                    Bitmap.Config.ARGB_8888
            )
            val rotateMatrix = Matrix()
            rotateMatrix.postRotate(90.0f)

            val rotatedBitmap = Bitmap.createBitmap(
                    imageBitmap, 0, 0, previewWidth, previewHeight,
                    rotateMatrix, true
            )
            image.close()
            frameCounter = (frameCounter + 1) % 3
            if (frameCounter == 0) {
                processImage(rotatedBitmap)
            }
        }
    }

    private fun cropBitmap(bitmap: Bitmap): Bitmap {
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width
        val modelInputRatio = MODEL_HEIGHT.toFloat() / MODEL_WIDTH
        var croppedBitmap = bitmap
        val maxDifference = 1e-5
        when {
            abs(modelInputRatio - bitmapRatio) < maxDifference -> return croppedBitmap
            modelInputRatio < bitmapRatio -> {
                val cropHeight = bitmap.height - (bitmap.width.toFloat() / modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        (cropHeight / 2).toInt(),
                        bitmap.width,
                        (bitmap.height - cropHeight).toInt()
                )
            }
            else -> {
                val cropWidth = bitmap.width - (bitmap.height.toFloat() * modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        (cropWidth / 2).toInt(),
                        0,
                        (bitmap.width - cropWidth).toInt(),
                        bitmap.height
                )
            }
        }
        return croppedBitmap
    }

    private fun setPaint() {
        paint.color = Color.RED
        paint.textSize = 80.0f
        paint.strokeWidth = 8.0f
    }

    /** Draw bitmap on Canvas.   */
    private fun draw(canvas: Canvas, person: Person, bitmap: Bitmap) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        // Draw `bitmap` and `person` in square canvas.
        val screenWidth: Int
        val screenHeight: Int
        val left: Int
        val right: Int
        val top: Int
        val bottom: Int
        if (canvas.height > canvas.width) {
            screenWidth = canvas.width
            screenHeight = canvas.width
            left = 0
            top = (canvas.height - canvas.width) / 2
        } else {
            screenWidth = canvas.height
            screenHeight = canvas.height
            left = (canvas.width - canvas.height) / 2
            top = 0
        }
        right = left + screenWidth
        bottom = top + screenHeight

        setPaint()
        canvas.drawBitmap(
                bitmap,
                Rect(0, 0, bitmap.width, bitmap.height),
                Rect(left, top, right, bottom),
                paint
        )

        val widthRatio = screenWidth.toFloat() / MODEL_WIDTH
        val heightRatio = screenHeight.toFloat() / MODEL_HEIGHT

        // Draw key points over the image.
        for (keyPoint in person.keyPoints) {
            if (keyPoint.score > minConfidence) {
                val position = keyPoint.position
                val adjustedX: Float = position.x.toFloat() * widthRatio + left
                val adjustedY: Float = position.y.toFloat() * heightRatio + top
                canvas.drawCircle(adjustedX, adjustedY, circleRadius, paint)
            }
        }

        for (line in bodyJoints) {
            if (
                    (person.keyPoints[line.first.ordinal].score > minConfidence) and
                    (person.keyPoints[line.second.ordinal].score > minConfidence)
            ) {
                canvas.drawLine(
                        person.keyPoints[line.first.ordinal].position.x.toFloat() * widthRatio + left,
                        person.keyPoints[line.first.ordinal].position.y.toFloat() * heightRatio + top,
                        person.keyPoints[line.second.ordinal].position.x.toFloat() * widthRatio + left,
                        person.keyPoints[line.second.ordinal].position.y.toFloat() * heightRatio + top,
                        paint
                )
            }
        }

        canvas.drawText(
                "Score: %.2f".format(person.score),
                (15.0f * widthRatio),
                (30.0f * heightRatio + bottom),
                paint
        )
        canvas.drawText(
                "Device: %s".format(poseNet.device),
                (15.0f * widthRatio),
                (50.0f * heightRatio + bottom),
                paint
        )
        canvas.drawText(
                "Time: %.2f ms".format(poseNet.lastInferenceTimeNanos * 1.0f / 1_000_000),
                (15.0f * widthRatio),
                (70.0f * heightRatio + bottom),
                paint
        )

        // Draw!
        surfaceHolder!!.unlockCanvasAndPost(canvas)
    }

    /** Process image using Pose net library.   */
    private fun processImage(bitmap: Bitmap) {
        val croppedBitmap = cropBitmap(bitmap)
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MODEL_WIDTH, MODEL_HEIGHT, true)
        val person = poseNet.estimateSinglePose(scaledBitmap)
        val canvas: Canvas = surfaceHolder!!.lockCanvas()
        draw(canvas, person, scaledBitmap)
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            imageReader = ImageReader.newInstance(
                    previewSize!!.width, previewSize!!.height, ImageFormat.YUV_420_888, 2
            )
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
            val recordingSurface = imageReader!!.surface
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder!!.addTarget(recordingSurface)
            cameraDevice!!.createCaptureSession(
                    listOf(recordingSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            if (cameraDevice == null) return
                            captureSession = cameraCaptureSession
                            try {
                                previewRequestBuilder!!.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                setAutoFlash(previewRequestBuilder!!)
                                previewRequest = previewRequestBuilder!!.build()
                                captureSession!!.setRepeatingRequest(
                                        previewRequest!!,
                                        captureCallback, backgroundHandler
                                )
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, e.toString())
                            }
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        }
                    },
                    null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (flashSupported) {
            requestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
        }
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
                AlertDialog.Builder(activity)
                        .setMessage(arguments?.getString(ARG_MESSAGE))
                        .setPositiveButton(android.R.string.ok) { _, _ -> activity?.finish() }
                        .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 1000
        private var DISPLAY_WIDTH = 700
        private var DISPLAY_HEIGHT = 1280
        private val ORIENTATIONS = SparseIntArray()
        private const val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        private const val TAG = "PosenetActivity"
    }
}
