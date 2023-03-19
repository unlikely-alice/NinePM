package unlikely.alice.ninepm

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun rememberAlarmEnabled() = rememberPreference(PreferenceKeys.ALARM_ENABLED, false)

@Composable
fun AlarmSwitch(until9pm: Duration) {
    var enabled by rememberAlarmEnabled()

    AnimatedVisibility(!until9pm.isNegative) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = enabled, onCheckedChange = { checked -> enabled = checked })
            Spacer(modifier = Modifier.size(4.dp))
            Text(text = if (enabled) "آلارم فعال است" else "آلارم غیرفعال است")
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "در صورت فعال بودن آلارم، ساعت 08:55 شب با یک صدای ملایم برنامه ی 9 شب را به شما یادآوری می‌کنیم.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }

    AlarmSetter()
}

// Configure the alarm manager and required permissions
@Composable
fun AlarmSetter() {
    val context = LocalContext.current
    var isAlarmEnabled by rememberAlarmEnabled()
    var openInvalidation by remember { mutableStateOf(0) }

    val alarmOpen = remember(openInvalidation, isAlarmEnabled) {
        if (isAlarmEnabled) needAlarmsPermission(context) else false
    }

    val notificationOpen = remember(openInvalidation, isAlarmEnabled, alarmOpen) {
        if (isAlarmEnabled && !alarmOpen) needNotificationsPermission(context) else false
    }

    // Listen to alarms permission changes
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DisposableEffect(Unit) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            val broadcast = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    openInvalidation++
                }
            }
            context.registerReceiver(broadcast, intentFilter)
            onDispose {
                context.unregisterReceiver(broadcast)
            }
        }
    }

    // Listen to notifications permission changes
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        DisposableEffect(Unit) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
            intentFilter.addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
            intentFilter.addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED)
            val broadcast = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    openInvalidation++
                }
            }
            context.registerReceiver(broadcast, intentFilter)
            onDispose {
                context.unregisterReceiver(broadcast)
            }
        }
    }

    if (alarmOpen) {
        AlertDialog(
            onDismissRequest = { isAlarmEnabled = false },
            title = {
                Text("مجوز زنگ آلارم")
            },
            text = {
                Text("این اپلیکیشن یک آلارم ثبت می کند تا هر شب ساعت 9 شما را یادآوری کند، ولی نسخه های جدید اندروید برای اضافه کردن زنگ آلارم نیاز به مجوز دارند.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + context.packageName)
                                )
                            )
                        }
                    }
                ) {
                    Text("دریافت مجوز آلارم")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isAlarmEnabled = false
                    }
                ) {
                    Text("لغو")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        openInvalidation++
    }

    if (notificationOpen) {
        AlertDialog(
            onDismissRequest = { isAlarmEnabled = false },
            title = {
                Text("مجوز اطلاع رسانی")
            },
            text = {
                Text("بعد از ثبت آلارم، لازم است در زمان وقوع آلارم به شما اطلاع رسانی کنیم، ولی برای اینکار در اندروید 13 به بعد نیاز به مجوز اطلاع رسانی یا همان notification داریم.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // TODO: If user denies this once or twice, we won't be able to show it again.
                            //  In that case we need to tell the user to do it manually or somehow open
                            //  the permissions page (like we do for notifications).
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("دریافت مجوز اطلاع رسانی")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isAlarmEnabled = false
                    }
                ) {
                    Text("لغو")
                }
            }
        )
    }

    // Set the alarm if enabled and have the required permissions
    if (!alarmOpen && !notificationOpen && isAlarmEnabled) {
        scheduleAlarm(context)
    } else {
        cancelAlarm(context)
    }
}

private val intentFlagImmutableUpdate =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    else
        PendingIntent.FLAG_UPDATE_CURRENT

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            // Reconfigure alarm after boot (because all alarms will be removed after reboot/shutdown)
            MainScope().launch {
                if (context.preferences.data.first()[PreferenceKeys.ALARM_ENABLED] == true) {
                    scheduleAlarm(context)
                }
            }
        } else {
            // Alarm fired
            // 1. Schedule alarm again for next day
            // 2. Show something to the user and play a sound
            scheduleAlarm(context)
            showNotification(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        val pendingIntent = PendingIntent.getActivity(context, LAUNCH_REQ_CODE, intent, intentFlagImmutableUpdate)

        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("9PM")
            .setContentText("5 دقیقه مانده تا راس ساعت 21")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setVibrate(longArrayOf(0, 250, 2500, 250, 2500, 250, 2500, 250, 2500, 250, 2500, 250))
            .setAutoCancel(true)
            .setSound(ALARM_NOTIFICATION_SOUND, AudioManager.STREAM_ALARM)

        NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notificationBuilder.build())
    }
}

fun scheduleAlarm(context: Context) {
    var zdt = ZonedDateTime.now()
        .withHour(20)
        .withMinute(55)
        .withSecond(0)

    if (zdt.isBefore(ZonedDateTime.now().plusSeconds(5))) {
        zdt = zdt.plusDays(1)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context.app,
        ALARM_REQ_CODE,
        Intent(context, AlarmReceiver::class.java),
        intentFlagImmutableUpdate
    )

    val intent = Intent(context, MainActivity::class.java)
    intent.flags =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_SINGLE_TOP
    val showIntent = PendingIntent.getActivity(context, LAUNCH_REQ_CODE, intent, intentFlagImmutableUpdate)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    alarmManager.setAlarmClock(
        AlarmManager.AlarmClockInfo(zdt.toInstant().toEpochMilli(), showIntent),
        pendingIntent
    )
}

fun cancelAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = PendingIntent.getBroadcast(
        context.app,
        ALARM_REQ_CODE,
        Intent(context, AlarmReceiver::class.java),
        intentFlagImmutableUpdate
    )
    alarmManager.cancel(pendingIntent)
}

fun needAlarmsPermission(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        !alarmManager.canScheduleExactAlarms()
    } else {
        false
    }
}

fun needNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    } else {
        false
    }
}

fun createAlarmNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.getNotificationChannel("alarm") == null) {
            val channel = NotificationChannel("alarm", "Alarm", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Used to show alarm"
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 250, 2500, 250, 2500, 250, 2500, 250, 2500, 250, 2500, 250)
            channel.setSound(
                ALARM_NOTIFICATION_SOUND, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}

fun turnScreenOnAndKeyguardOff(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
    } else {
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    with(activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestDismissKeyguard(activity, null)
        }
    }
}

const val ALARM_REQ_CODE = 14986544
const val LAUNCH_REQ_CODE = 4557844
const val ALARM_NOTIFICATION_ID = 318012
val ALARM_NOTIFICATION_SOUND: Uri = Uri.parse(
    "android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.alarm
)