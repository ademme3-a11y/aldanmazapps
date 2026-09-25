package com.aldanmaz.drivedashboard.data.compass

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class CompassReading(
    val headingDegrees: Float?,
    val accuracy: CompassSensorAccuracy,
)

enum class CompassSensorAccuracy {
    UNAVAILABLE,
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH,
}

class CompassTracker(context: Context) : SensorEventListener {

    private val applicationContext = context.applicationContext
    private val sensorManager =
        applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager =
        applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val rotationVectorSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticFieldSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val displayAdjustedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val accelerometerValues = FloatArray(3)
    private val magneticFieldValues = FloatArray(3)

    private var hasAccelerometerValues = false
    private var hasMagneticFieldValues = false
    private var declinationDegrees = 0f
    private var smoothedHeadingDegrees: Float? = null
    private var latestAccuracy = CompassSensorAccuracy.UNRELIABLE
    private var readingListener: ((CompassReading) -> Unit)? = null

    val isAvailable: Boolean
        get() =
            rotationVectorSensor != null ||
                    (accelerometerSensor != null && magneticFieldSensor != null)

    fun start(onReading: (CompassReading) -> Unit) {
        stop()
        readingListener = onReading

        if (!isAvailable) {
            latestAccuracy = CompassSensorAccuracy.UNAVAILABLE
            emitReading(null)
            return
        }

        latestAccuracy = CompassSensorAccuracy.UNRELIABLE

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
        } else {
            accelerometerSensor?.let { sensor ->
                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }
            magneticFieldSensor?.let { sensor ->
                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        readingListener = null
    }

    fun updateLocation(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        timeMillis: Long = System.currentTimeMillis(),
    ) {
        val geomagneticField =
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters.toFloat(),
                timeMillis,
            )
        declinationDegrees = geomagneticField.declination
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                calculateAndEmitHeading(rotationMatrix)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                copyThreeValues(event.values, accelerometerValues)
                hasAccelerometerValues = true
                calculateFallbackHeadingIfReady()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                copyThreeValues(event.values, magneticFieldValues)
                hasMagneticFieldValues = true
                calculateFallbackHeadingIfReady()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (
            sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            sensor.type != Sensor.TYPE_MAGNETIC_FIELD
        ) {
            return
        }

        latestAccuracy = accuracy.toCompassAccuracy()
        emitReading(smoothedHeadingDegrees)
    }

    private fun calculateFallbackHeadingIfReady() {
        if (!hasAccelerometerValues || !hasMagneticFieldValues) return

        val rotationMatrixReady =
            SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerValues,
                magneticFieldValues,
            )

        if (rotationMatrixReady) {
            calculateAndEmitHeading(rotationMatrix)
        }
    }

    private fun calculateAndEmitHeading(sourceRotationMatrix: FloatArray) {
        remapForCurrentDisplay(
            source = sourceRotationMatrix,
            destination = displayAdjustedMatrix,
        )

        SensorManager.getOrientation(displayAdjustedMatrix, orientation)

        val magneticHeadingDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val trueHeadingDegrees = normalizeDegrees(magneticHeadingDegrees + declinationDegrees)
        val smoothHeading = smoothCircularHeading(trueHeadingDegrees)

        smoothedHeadingDegrees = smoothHeading
        emitReading(smoothHeading)
    }

    @Suppress("DEPRECATION")
    private fun remapForCurrentDisplay(
        source: FloatArray,
        destination: FloatArray,
    ) {
        val rotation = windowManager.defaultDisplay.rotation

        val (axisX, axisY) =
            when (rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 ->
                    SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }

        SensorManager.remapCoordinateSystem(
            source,
            axisX,
            axisY,
            destination,
        )
    }

    private fun smoothCircularHeading(newHeadingDegrees: Float): Float {
        val previousHeading = smoothedHeadingDegrees ?: return newHeadingDegrees
        val shortestDifference =
            ((newHeadingDegrees - previousHeading + 540f) % 360f) - 180f

        return normalizeDegrees(previousHeading + shortestDifference * SMOOTHING_FACTOR)
    }

    private fun emitReading(headingDegrees: Float?) {
        readingListener?.invoke(
            CompassReading(
                headingDegrees = headingDegrees,
                accuracy = latestAccuracy,
            )
        )
    }

    private fun copyThreeValues(
        source: FloatArray,
        destination: FloatArray,
    ) {
        destination[0] = source[0]
        destination[1] = source[1]
        destination[2] = source[2]
    }

    private fun Int.toCompassAccuracy(): CompassSensorAccuracy =
        when (this) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassSensorAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassSensorAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassSensorAccuracy.LOW
            else -> CompassSensorAccuracy.UNRELIABLE
        }

    private fun normalizeDegrees(degrees: Float): Float =
        ((degrees % 360f) + 360f) % 360f

    private companion object {
        const val SMOOTHING_FACTOR = 0.18f
    }
}

fun calculateQiblaBearingDegrees(
    latitude: Double,
    longitude: Double,
): Float {
    val latitudeRadians = latitude.toRadians()
    val longitudeDifferenceRadians = (KAABA_LONGITUDE - longitude).toRadians()
    val kaabaLatitudeRadians = KAABA_LATITUDE.toRadians()

    val y = sin(longitudeDifferenceRadians)
    val x =
        cos(latitudeRadians) * tan(kaabaLatitudeRadians) -
                sin(latitudeRadians) * cos(longitudeDifferenceRadians)

    val bearingDegrees = atan2(y, x) * 180.0 / PI
    return (((bearingDegrees % 360.0) + 360.0) % 360.0).toFloat()
}

private fun Double.toRadians(): Double = this * PI / 180.0

private const val KAABA_LATITUDE = 21.422487
private const val KAABA_LONGITUDE = 39.826206

