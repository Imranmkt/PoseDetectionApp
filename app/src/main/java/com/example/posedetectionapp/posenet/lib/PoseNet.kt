package com.example.posedetectionapp.posenet.lib

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate


class PoseNet(val context: Context, private val filename: String = "posenet_model.tflite", val device: Device = Device.CPU) : AutoCloseable {
    var lastInferenceTimeNanos: Long = -1
        private set

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val numLiteThreads = 4

    private fun getInterpreter(): Interpreter {
        if (interpreter != null) {
            return interpreter!!
        }
        val options = Interpreter.Options()
        options.setNumThreads(numLiteThreads)
        when (device) {
            Device.CPU -> {
            }
            Device.GPU -> {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            }
            Device.NNAPI -> options.setUseNNAPI(true)
        }
        interpreter = Interpreter(loadModelFile(filename, context), options)
        return interpreter!!
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    private fun sigmoid(x: Float): Float = (1.0f / (1.0f + exp(-x)))

    private fun initInputArray(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = 4
        val inputChannels = 3
        val batchSize = 1
        val inputBuffer = ByteBuffer.allocateDirect(
                batchSize * bytesPerChannel * bitmap.height * bitmap.width * inputChannels
        )
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val mean = 128.0f
        val std = 128.0f
        for (row in 0 until bitmap.height) {
            for (col in 0 until bitmap.width) {
                val pixelValue = bitmap.getPixel(col, row)
                inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - mean) / std)
                inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - mean) / std)
                inputBuffer.putFloat(((pixelValue and 0xFF) - mean) / std)
            }
        }
        return inputBuffer
    }

    private fun loadModelFile(path: String, context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength
        )
    }

    private fun initOutputMap(interpreter: Interpreter): HashMap<Int, Any> {
        val outputMap = HashMap<Int, Any>()
        val heatMapsShape = interpreter.getOutputTensor(0).shape()
        outputMap[0] = Array(heatMapsShape[0]) {
            Array(heatMapsShape[1]) {
                Array(heatMapsShape[2]) { FloatArray(heatMapsShape[3]) }
            }
        }
        val offsetsShape = interpreter.getOutputTensor(1).shape()
        outputMap[1] = Array(offsetsShape[0]) {
            Array(offsetsShape[1]) { Array(offsetsShape[2]) { FloatArray(offsetsShape[3]) } }
        }
        val displacementsFwdShape = interpreter.getOutputTensor(2).shape()
        outputMap[2] = Array(offsetsShape[0]) {
            Array(displacementsFwdShape[1]) {
                Array(displacementsFwdShape[2]) { FloatArray(displacementsFwdShape[3]) }
            }
        }

        val displacementsBwdShape = interpreter.getOutputTensor(3).shape()
        outputMap[3] = Array(displacementsBwdShape[0]) {
            Array(displacementsBwdShape[1]) {
                Array(displacementsBwdShape[2]) { FloatArray(displacementsBwdShape[3]) }
            }
        }

        return outputMap
    }

    fun estimateSinglePose(bitmap: Bitmap): Person {
        val inputArray = arrayOf(initInputArray(bitmap))
        val outputMap = initOutputMap(getInterpreter())

        val inferenceStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        getInterpreter().runForMultipleInputsOutputs(inputArray, outputMap)
        lastInferenceTimeNanos = SystemClock.elapsedRealtimeNanos() - inferenceStartTimeNanos

        val heatMaps = outputMap[0] as Array<Array<Array<FloatArray>>>
        val offsets = outputMap[1] as Array<Array<Array<FloatArray>>>

        val height = heatMaps[0].size
        val width = heatMaps[0][0].size
        val numKeyPoints = heatMaps[0][0][0].size

        val keyPointPositions = Array(numKeyPoints) { Pair(0, 0) }
        for (keyPoint in 0 until numKeyPoints) {
            var maxVal = heatMaps[0][0][0][keyPoint]
            var maxRow = 0
            var maxCol = 0
            for (row in 0 until height) {
                for (col in 0 until width) {
                    if (heatMaps[0][row][col][keyPoint] > maxVal) {
                        maxVal = heatMaps[0][row][col][keyPoint]
                        maxRow = row
                        maxCol = col
                    }
                }
            }
            keyPointPositions[keyPoint] = Pair(maxRow, maxCol)
        }

        // Calculating the x and y coordinates of the key points with offset adjustment.
        val xCord = IntArray(numKeyPoints)
        val yCord = IntArray(numKeyPoints)
        val confidenceScores = FloatArray(numKeyPoints)
        keyPointPositions.forEachIndexed { idx, position ->
            val positionY = keyPointPositions[idx].first
            val positionX = keyPointPositions[idx].second
            yCord[idx] = (
                    position.first / (height - 1).toFloat() * bitmap.height +
                            offsets[0][positionY][positionX][idx]
                    ).toInt()
            xCord[idx] = (
                    position.second / (width - 1).toFloat() * bitmap.width +
                            offsets[0][positionY]
                                    [positionX][idx + numKeyPoints]
                    ).toInt()
            confidenceScores[idx] = sigmoid(heatMaps[0][positionY][positionX][idx])
        }

        val person = Person()
        val keyPointList = Array(numKeyPoints) { KeyPoint() }
        var totalScore = 0.0f
        enumValues<BodyPart>().forEachIndexed { idx, it ->
            keyPointList[idx].bodyPart = it
            keyPointList[idx].position.x = xCord[idx]
            keyPointList[idx].position.y = yCord[idx]
            keyPointList[idx].score = confidenceScores[idx]
            totalScore += confidenceScores[idx]
        }

        person.keyPoints = keyPointList.toList()
        person.score = totalScore / numKeyPoints

        return person
    }
}
