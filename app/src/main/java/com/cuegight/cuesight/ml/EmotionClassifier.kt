package com.cuegight.cuesight.ml

import android.content.Context
import android.graphics.Bitmap
import com.cuegight.cuesight.R
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmotionClassifier(context: Context) : Closeable {
    private val interpreter: Interpreter
    private val inputBuffer: ByteBuffer

    val labels: List<String> = context.resources
        .getStringArray(R.array.emotion_labels)
        .toList()

    init {
        val modelBytes = context.resources.openRawResource(R.raw.mobilenetv3_fer).use { it.readBytes() }
        interpreter = Interpreter(modelBytes, Interpreter.Options().setNumThreads(2))
        inputBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * INPUT_SIZE * INPUT_SIZE * CHANNELS * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
    }

    fun classify(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        inputBuffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index]
            val r = ((pixel shr 16) and 0xFF) / NORMALIZATION_DIVISOR - NORMALIZATION_SHIFT
            val g = ((pixel shr 8) and 0xFF) / NORMALIZATION_DIVISOR - NORMALIZATION_SHIFT
            val b = (pixel and 0xFF) / NORMALIZATION_DIVISOR - NORMALIZATION_SHIFT
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
            index++
        }
        val output = Array(BATCH_SIZE) { FloatArray(labels.size) }
        interpreter.run(inputBuffer, output)
        return output[0]
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        // Normalize RGB from [0, 255] to [-1, 1] for MobileNetV3-style inputs.
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3
        private const val BATCH_SIZE = 1
        private const val FLOAT_SIZE = 4
        private const val NORMALIZATION_DIVISOR = 127.5f
        private const val NORMALIZATION_SHIFT = 1f
    }
}
