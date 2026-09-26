package com.aldanmaz.drivedashboard.ui.screen.weather

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.aldanmaz.drivedashboard.R
import com.aldanmaz.drivedashboard.data.weather.WeatherCondition

/**
 * AldanmazDrive_10: Hava arka planı artık sıcaklığa göre çizilen eski vektör sahne yerine
 * gerçek hava olayına göre 16:9 sinematik görsel seçer.
 */
@Composable
fun WeatherTemperatureScene(
    temperatureCelsius: Double?,
    condition: WeatherCondition = WeatherCondition.UNKNOWN,
    isDay: Boolean = true,
    windSpeedKmh: Double? = null,
    windGustKmh: Double? = null,
    modifier: Modifier = Modifier
) {
    val drawable = weatherBackgroundFor(
        condition = condition,
        isDay = isDay,
        windSpeedKmh = windSpeedKmh,
        windGustKmh = windGustKmh
    )

    Box(modifier = modifier.background(Color(0xFF07111C))) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Yazıların gündüz / açık hava görsellerinde de okunaklı kalması için hafif karartma.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
    }
}

@DrawableRes
private fun weatherBackgroundFor(
    condition: WeatherCondition,
    isDay: Boolean,
    windSpeedKmh: Double?,
    windGustKmh: Double?
): Int {
    val strongWind = (windGustKmh ?: 0.0) >= 50.0 || (windSpeedKmh ?: 0.0) >= 35.0

    // Şiddetli yağış / fırtına / kar gibi olaylar rüzgâr görselinden daha öncelikli.
    return when (condition) {
        WeatherCondition.THUNDERSTORM -> R.drawable.weather_thunderstorm
        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS,
        WeatherCondition.FREEZING_RAIN -> R.drawable.weather_snow
        WeatherCondition.FOG -> R.drawable.weather_fog
        WeatherCondition.RAIN,
        WeatherCondition.RAIN_SHOWERS -> R.drawable.weather_rain_heavy
        WeatherCondition.DRIZZLE -> R.drawable.weather_rain_light
        WeatherCondition.CLOUDY -> if (strongWind) R.drawable.weather_windy else R.drawable.weather_overcast
        WeatherCondition.PARTLY_CLOUDY -> if (strongWind) {
            R.drawable.weather_windy
        } else if (isDay) {
            R.drawable.weather_partly_cloudy_day
        } else {
            R.drawable.weather_partly_cloudy_night
        }
        WeatherCondition.CLEAR -> if (strongWind) {
            R.drawable.weather_windy
        } else if (isDay) {
            R.drawable.weather_clear_day
        } else {
            R.drawable.weather_clear_night
        }
        WeatherCondition.UNKNOWN -> if (strongWind) R.drawable.weather_windy else R.drawable.weather_cloudy
    }
}
