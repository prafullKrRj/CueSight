package com.cuegight.cuesight.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Saves an image from URI to app's internal storage
     * Returns the file path as a string
     */
    fun saveImageToInternalStorage(context: Context, imageUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null

            // Decode and compress the image
            val bitmap = decodeSampledBitmapFromInputStream(inputStream, 512, 512)
            inputStream.close()

            // Rotate bitmap if needed based on EXIF data
            val rotatedBitmap = rotateImageIfRequired(context, bitmap, imageUri)

            // Create a unique filename
            val filename = "student_${UUID.randomUUID()}.jpg"
            val directory = File(context.filesDir, "student_photos")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)

            // Save the bitmap to file
            FileOutputStream(file).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            // Clean up
            if (bitmap != rotatedBitmap) {
                bitmap.recycle()
            }
            rotatedBitmap.recycle()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a sampled bitmap from input stream to reduce memory usage
     */
    private fun decodeSampledBitmapFromInputStream(
        inputStream: InputStream,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        val bytes = inputStream.readBytes()

        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)

            // Calculate inSampleSize
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        }
    }

    /**
     * Calculates the sample size to reduce bitmap size
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Rotates the image based on EXIF orientation data
     */
    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap {
        val input = context.contentResolver.openInputStream(selectedImage) ?: return img

        return try {
            val ei = ExifInterface(input)
            val orientation = ei.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
                else -> img
            }
        } catch (e: Exception) {
            e.printStackTrace()
            img
        } finally {
            input.close()
        }
    }

    /**
     * Rotates the bitmap by the specified degrees
     */
    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

    /**
     * Deletes an image file from internal storage
     */
    fun deleteImageFromInternalStorage(imagePath: String?): Boolean {
        if (imagePath.isNullOrEmpty()) return false

        return try {
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Loads bitmap from internal storage file path
     */
    fun loadImageFromInternalStorage(imagePath: String?): Bitmap? {
        if (imagePath.isNullOrEmpty()) return null

        return try {
            val file = File(imagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

