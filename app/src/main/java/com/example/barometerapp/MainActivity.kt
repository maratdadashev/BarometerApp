package com.example.barometerapp

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.pow

class BarometerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    var pressure by mutableStateOf<Float?>(null)
        private set

    var altitude by mutableStateOf<Float?>(null)
        private set

    var weatherTrend by mutableStateOf("Анализ данных...")
        private set

    var seaLevelPressure by mutableStateOf(1013.25f)
        private set

    private val _pressureHistory = mutableStateListOf<Float>()
    val pressureHistory: List<Float> get() = _pressureHistory

    val isSensorAvailable: Boolean = pressureSensor != null

    fun startListening() {
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun calibrateZeroAltitude() {
        pressure?.let { current ->
            seaLevelPressure = current
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val currentPressure = event.values[0]
            pressure = currentPressure

            val alt = 44330f * (1f - (currentPressure / seaLevelPressure).pow(0.1903f))
            altitude = alt

            _pressureHistory.add(currentPressure)
            if (_pressureHistory.size > 30) {
                _pressureHistory.removeAt(0)
            }

            if (_pressureHistory.size >= 10) {
                val firstPoint = _pressureHistory.first()
                val lastPoint = _pressureHistory.last()
                val diff = lastPoint - firstPoint

                weatherTrend = when {
                    diff < -1.5f -> "⚠️ Резкое падение: шторм / дождь"
                    diff < -0.5f -> "☂️ Давление падает: к ухудшению погоды"
                    diff > 1.5f -> "☀️ Резкий рост: к сильному прояснению"
                    diff > 0.5f -> "⛅ Давление растет: к улучшению"
                    else -> "☁️ Стабильная погода"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun PressureChart(points: List<Float>) {
    if (points.size < 2) return

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val minP = points.minOrNull() ?: 0f
        val maxP = points.maxOrNull() ?: 1f
        val range = if (maxP - minP == 0f) 1f else maxP - minP

        val widthStep = size.width / (points.size - 1)
        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { index, value ->
            val x = index * widthStep
            val y = size.height - ((value - minP) / range) * (size.height * 0.7f) - (size.height * 0.15f)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.25f),
                    primaryColor.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = size.height
            )
        )

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BarometerScreen()
                }
            }
        }
    }
}

@Composable
fun BarometerScreen(viewModel: BarometerViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startListening()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopListening()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Барометр OnePlus 13",
            style = MaterialTheme.colorScheme.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isSensorAvailable) {
            val hPa = viewModel.pressure ?: 0f
            val mmHg = hPa * 0.750062f
            val altitude = viewModel.altitude ?: 0f

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = viewModel.weatherTrend,
                            style = MaterialTheme.colorScheme.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Атмосферное давление",
                        style = MaterialTheme.colorScheme.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f гПа", hPa),
                        style = MaterialTheme.colorScheme.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f мм рт. ст.", mmHg),
                        style = MaterialTheme.colorScheme.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.width(100.dp))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Относительная высота",
                        style = MaterialTheme.colorScheme.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f м", altitude),
                        style = MaterialTheme.colorScheme.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.calibrateZeroAltitude() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = "📍 Калибровать (принять за 0 м)")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Динамика изменения",
                        style = MaterialTheme.colorScheme.labelMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PressureChart(points = viewModel.pressureHistory)
                }
            }
        } else {
            Text(
                text = "Ошибка: Датчик давления не найден.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.colorScheme.bodyLarge
            )
        }
    }
}
