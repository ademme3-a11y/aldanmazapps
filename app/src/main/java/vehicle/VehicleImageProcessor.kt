

package com.aldanmaz.drivedashboard.data.vehicle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream

object VehicleImageProcessor {

    fun processVehicleImage(
        context: Context,
        sourceUri: Uri,
        vehicleType: VehicleType,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val inputImage =
            runCatching {
                InputImage.fromFilePath(
                    context,
                    sourceUri
                )
            }.getOrElse { error ->
                onError(error)
                return
            }

        val options =
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()

        val segmenter =
            SubjectSegmentation.getClient(options)

        segmenter
            .process(inputImage)
            .addOnSuccessListener { result ->
                try {
                    val foregroundBitmap =
                        result.foregroundBitmap
                            ?: error(
                                "Araç görseli arka plandan ayrılamadı."
                            )

                    val trimmedBitmap =
                        trimTransparentEdges(
                            foregroundBitmap
                        )

                    val savedUri =
                        saveAsTransparentPng(
                            context = context,
                            bitmap = trimmedBitmap,
                            vehicleType = vehicleType
                        )

                    if (
                        trimmedBitmap !== foregroundBitmap &&
                        !foregroundBitmap.isRecycled
                    ) {
                        foregroundBitmap.recycle()
                    }

                    onSuccess(savedUri)

                } catch (error: Throwable) {
                    onError(error)

                } finally {
                    segmenter.close()
                }
            }
            .addOnFailureListener { error ->
                segmenter.close()
                onError(error)
            }
    }

    private fun saveAsTransparentPng(
        context: Context,
        bitmap: Bitmap,
        vehicleType: VehicleType
    ): Uri {

        val directory =
            File(
                context.filesDir,
                "vehicle_images"
            ).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

        val fileName =
            when (vehicleType) {
                VehicleType.CAR ->
                    "custom_car.png"

                VehicleType.CARAVAN ->
                    "custom_caravan.png"
            }

        val outputFile =
            File(
                directory,
                fileName
            )

        FileOutputStream(outputFile).use { stream ->
            val saved =
                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    stream
                )

            if (!saved) {
                error(
                    "Şeffaf PNG kaydedilemedi."
                )
            }

            stream.flush()
        }

        return Uri.fromFile(outputFile)
    }

    /**
     * ML Kit çıktısının şeffaf kenarlarını kırpar.
     *
     * Böylece dashboard üzerinde fotoğrafın tamamı yerine
     * yalnızca ayrıştırılmış araç/karavan daha büyük görünür.
     */
    private fun trimTransparentEdges(
        bitmap: Bitmap
    ): Bitmap {

        val width = bitmap.width
        val height = bitmap.height

        if (
            width <= 1 ||
            height <= 1
        ) {
            return bitmap
        }

        var left = width
        var top = height
        var right = -1
        var bottom = -1

        val pixels =
            IntArray(width)

        for (y in 0 until height) {

            bitmap.getPixels(
                pixels,
                0,
                width,
                0,
                y,
                width,
                1
            )

            for (x in 0 until width) {

                val alpha =
                    Color.alpha(
                        pixels[x]
                    )

                if (alpha > ALPHA_THRESHOLD) {

                    if (x < left) {
                        left = x
                    }

                    if (x > right) {
                        right = x
                    }

                    if (y < top) {
                        top = y
                    }

                    if (y > bottom) {
                        bottom = y
                    }
                }
            }
        }

        if (
            right < left ||
            bottom < top
        ) {
            return bitmap
        }

        val paddingX =
            ((right - left + 1) *
                    CROP_PADDING_RATIO)
                .toInt()
                .coerceAtLeast(1)

        val paddingY =
            ((bottom - top + 1) *
                    CROP_PADDING_RATIO)
                .toInt()
                .coerceAtLeast(1)

        val cropLeft =
            (left - paddingX)
                .coerceAtLeast(0)

        val cropTop =
            (top - paddingY)
                .coerceAtLeast(0)

        val cropRight =
            (right + paddingX)
                .coerceAtMost(width - 1)

        val cropBottom =
            (bottom + paddingY)
                .coerceAtMost(height - 1)

        val cropWidth =
            cropRight -
                    cropLeft +
                    1

        val cropHeight =
            cropBottom -
                    cropTop +
                    1

        if (
            cropWidth >= width &&
            cropHeight >= height
        ) {
            return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            cropLeft,
            cropTop,
            cropWidth,
            cropHeight
        )
    }

    private const val ALPHA_THRESHOLD = 8

    private const val CROP_PADDING_RATIO = 0.04f
}
