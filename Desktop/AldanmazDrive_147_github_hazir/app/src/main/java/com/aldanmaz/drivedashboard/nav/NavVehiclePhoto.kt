package com.aldanmaz.drivedashboard.nav

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aldanmaz.drivedashboard.data.vehicle.VehicleImageProcessor
import com.aldanmaz.drivedashboard.data.vehicle.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

/**
 * 3B surus ekranindaki arac gorseli icin kullanicinin KENDI arac fotografi (arkadan cekilmis).
 * Foto, uygulamanin mevcut ML Kit arka plan temizleme hattindan gecirilir ve ayri bir dosyada saklanir;
 * dashboard'daki "ozel arac gorseli" (custom_car.png) bozulmaz (islem sonrasi eski haline geri yuklenir).
 */
object NavVehiclePhoto {
    private fun dir(ctx: Context) = File(ctx.filesDir, "vehicle_images").apply { if (!exists()) mkdirs() }
    fun file(ctx: Context) = File(dir(ctx), "nav_rear.png")

    suspend fun load(ctx: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (!f.exists()) return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1400) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } catch (t: Throwable) {
            null
        }
    }

    fun reset(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    /** onResult arka plan is parcaciginda cagrilabilir. */
    fun import(ctx: Context, source: Uri, onResult: (Result<Unit>) -> Unit) {
        val custom = File(dir(ctx), "custom_car.png")
        val backup = File(ctx.cacheDir, "custom_car_backup.png")
        val hadCustom = custom.exists()
        try {
            if (hadCustom) custom.copyTo(backup, overwrite = true)
        } catch (t: Throwable) {
            onResult(Result.failure(t)); return
        }
        fun restore() {
            runCatching { if (hadCustom) backup.copyTo(custom, overwrite = true) else custom.delete() }
        }
        VehicleImageProcessor.processVehicleImage(
            context = ctx, sourceUri = source, vehicleType = VehicleType.CAR,
            onSuccess = { out ->
                thread(name = "nav-photo-save") {
                    try {
                        val src = File(requireNotNull(out.path))
                        src.copyTo(file(ctx), overwrite = true)
                        restore()
                        onResult(Result.success(Unit))
                    } catch (t: Throwable) {
                        restore()
                        onResult(Result.failure(t))
                    }
                }
            },
            onError = { t ->
                restore()
                onResult(Result.failure(t))
            }
        )
    }
}
