package unlikely.alice.ninepm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

@Composable
fun MainScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        val until9pm = durationUntil9PM()
        Header()
        Spacer(modifier = Modifier.weight(1f))
        Daily9Pm(until9pm)
        if (!until9pm.isNegative) Spacer(modifier = Modifier.weight(1f))
        AlarmSwitch(until9pm)
    }
}

@Composable
fun ColumnScope.Daily9Pm(until9pm: Duration) {
    AnimatedVisibility(until9pm.isNegative) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(9.dp)
                )
                .padding(16.dp),
        ) {
            Daily9PmContent()
        }
    }

    if (until9pm.isNegative) Spacer(modifier = Modifier.weight(1f))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (until9pm.isNegative) "باقی مانده از 5 دقیقه" else "مانده تا رأس ساعت 21",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.alpha(0.8f)
        )
        Ltr {
            Text(
                "%02d : %02d : %02d".format(
                    until9pm.toHoursPart().absoluteValue,
                    until9pm.toMinutesPart().absoluteValue,
                    until9pm.toSecondsPart().absoluteValue,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
fun Daily9PmContent(modifier: Modifier = Modifier) {
    val nearbyCount by LocalContext.current.app.bleCounter.nearbyCount.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        if (nearbyCount == 0) {
            BleCounterPermissionsUi()
        } else {
            val areLightsOff = lightsOffSensor()
            if (areLightsOff) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(MaterialTheme.typography.displayMedium.toSpanStyle()) { append("${nearbyCount - 1}") }
                        append("\n")
                        if (nearbyCount == 2) {
                            withStyle(MaterialTheme.typography.bodyMedium.toSpanStyle()) { append("نفر در نزدیکیتان با شما همراه شده است.") }
                        } else {
                            withStyle(MaterialTheme.typography.bodyMedium.toSpanStyle()) { append("نفر در نزدیکیتان با شما همراه شده‌اند.") }
                        }
                    },
                    textAlign = TextAlign.Center
                )
                Text(
                    "اگر تعداد به حد کافی بالا باشد، می‌دانید اگر شعار دهید تنها نخواهید بود.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 8.dp)
                        .alpha(0.8f),
                )
            } else {
                Text(text = "لطفا چراغ‌ها را خاموش کنید!")
            }
        }
    }
}

@Composable
fun lightsOffSensor(): Boolean {
    var areLightsOff by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val lightSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.get(0)?.let { value ->
                    areLightsOff = value == 0f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return areLightsOff
}

@Composable
fun durationUntil9PM(): Duration {
    var until9pm by remember { mutableStateOf(getDurationUntil9PM()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            until9pm = getDurationUntil9PM()
            delay(500)
        }
    }

    return until9pm
}

fun getDurationUntil9PM(): Duration {
    val now = OffsetDateTime.now()
    val bufferMinutes = 5
    val is9pm = now.hour == 21 && now.minute < bufferMinutes
    return if (is9pm) {
        Duration.between(now.withMinute(bufferMinutes).withSecond(0), now)
    } else {
        val next9PM = if (now.hour >= 21) {
            now.plus(1, ChronoUnit.DAYS)
                .withHour(21)
                .truncatedTo(ChronoUnit.HOURS)
        } else {
            now.withHour(21)
                .truncatedTo(ChronoUnit.HOURS)
        }
        Duration.between(now, next9PM)
    }
}

@Composable
fun Header() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "نه به جمهوریِ دیکتاتوری",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Row {
            ElevatedButton(onClick = { openWeb(context, "https://mardomi.pages.dev/ninepm/ninepm-app/") }) {
                Text("درباره")
            }
            Spacer(Modifier.size(16.dp))
            ElevatedButton(onClick = { openWeb(context, "https://mardomi.pages.dev/ninepm/ninepm-app/#%D8%AF%D8%A7%D9%86%D9%84%D9%88%D8%AF-%D9%88-%D9%86%D8%B5%D8%A8") }) {
                Text("به روز رسانی")
            }
        }
    }
}