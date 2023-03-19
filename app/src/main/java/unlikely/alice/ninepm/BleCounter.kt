package unlikely.alice.ninepm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * Use bluetooth to find number of nearby devices.
 *
 * All devices start advertising and scanning, this way we can count how many devices
 * are within bluetooh range. We can double this range with the following technique:
 *
 * Each device gets assigned a random 3-byte id on first app launch after install.
 * With 3 bytes ids, we have space for 9 ids in a single ble advertisement packet.
 * The first id is the id of the advertiser, we need this because android/ios will
 * randomly change mac address after a while, so we need our own unique id that
 * doesn't change. A scanner device will find n packets with different ids which
 * means n other devices are nearby. Now this scanner selects 8 of those devices
 * and adds their ids after its own id in its own advertisement data segment.
 * Other scanners will receive these packets and will know in addition to the first
 * scanner, 8 other devices are also nearby. These extra ids are only selected from
 * the devices a scanner sees itself (not from extra ids received from advertisement data),
 * otherwise the definition of "nearby" could grow arbitrarily large. Since we are forwarding
 * nearby devices once, this doubles the range which is good enough.
 *
 * There are 16 million possibilities a 3 byte id can have.
 * You need more than 1500 nearby devices to have a 10% chance of collision (see bdayprob.com).
 * Since collision is not fatal for our use case, this is good enough.
 *
 */
@SuppressLint("MissingPermission")
class BleCounter(app: AndroidApp) {
    // 16bit uuid form: "0000xxxx-0000-1000-8000-00805F9B34FB"
    // See https://btprodspecificationrefs.blob.core.windows.net/assigned-values/16-bit%20UUID%20Numbers%20Document.pdf
    // FD1B is for Helios Sports, Inc.
    // If apple doesn't allow us to use a well known uuid for ios, we can use a full uuid for iphones and send fewer
    // nearby-ids in the advertisement data section. Android devices can continue to send more ids in the data section.

    private val uuid = ParcelUuid.fromString("0000FD1B-0000-1000-8000-00805F9B34FB")
    private val adapter by lazy { (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val myId by lazy {
        var id = runBlocking { app.preferences.data.first()[PreferenceKeys.BLUETOOTH_ID] }
        if (id == null) {
            val idBytes = ByteArray(3)
            SecureRandom().nextBytes(idBytes)
            id = idBytes.base64()
            runBlocking { app.preferences.edit { it[PreferenceKeys.BLUETOOTH_ID] = id!! } }
        }
        id!!
    }

    private val nearbyIds = mutableSetOf<String>()
    private val forwardedIds = mutableSetOf<String>()

    private val _nearbyCount = MutableStateFlow(0)

    // -1 means we're not counting
    val nearbyCount: StateFlow<Int> = _nearbyCount

    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private val scanTimeout = 20000
    private val advertiseTimeout = 10000 // Lower to quickly switch between ids to forward

    init {
        // Start ble counter operation as soon as we have the permissions and bluetooth and location is enabled.
        MainScope().launch {
            app.permissionsManager.state.collectLatest { permissionState ->
                // Restart scanning/advertising after a while in case a phone tries to be cute and stops
                // on its own without telling me. Also do not start scan more than 4 times every 30s.
                // See Notable scan errors in this article: https://punchthrough.com/android-ble-guide/
                if (permissionState.canStartBleCounter) {
                    try {
                        _nearbyCount.value = 1 // At least ourself
                        val scanLoop = launch { runScanLoop() }
                        val advertiseLoop = launch { runAdvertisingLoop() }
                        scanLoop.join()
                        advertiseLoop.join()
                    } finally {
                        // Cleanup when permissionState changes
                        _nearbyCount.value = 0
                        nearbyIds.clear()
                        forwardedIds.clear()
                        stopAdvertising()
                        stopScan()
                    }
                }
            }
        }
    }

    private fun startScan() {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.scanRecord?.serviceData?.get(uuid)?.let { data ->
                    if (data.size >= 3) nearbyIds.add(data.base64(0))
                    for (i in 1 until 9) {
                        if (data.size >= (i + 1) * 3) {
                            val forwardedId = data.base64(i * 3)
                            if (forwardedId != myId) {
                                forwardedIds.add(forwardedId)
                            }
                        }
                    }
                    _nearbyCount.value = (nearbyIds + forwardedIds).size + 1 // +1 for ourself
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("nearby", "scan failed")
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceData(uuid, null, null)
            .build()


        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            settings.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }

        adapter.bluetoothLeScanner?.startScan(listOf(filter), settings.build(), scanCallback)
    }

    private fun stopScan() {
        scanCallback?.let {
            adapter.bluetoothLeScanner?.stopScan(it)
            scanCallback = null
        }
    }

    private fun startAdvertising() {
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i("nearby", "advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("nearby", "advertising failed: $errorCode")
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setTimeout(advertiseTimeout)
            .setConnectable(false)
            .build()

        // Randomly select maximum of 8 ids to forward
        val idsToForward = mutableSetOf<String>()
        while (idsToForward.size < Integer.min(nearbyIds.size, 8)) {
            idsToForward.add(nearbyIds.random())
        }

        // Build the service data
        val buffer = ByteBuffer.allocate(27)
        buffer.put(myId.base64())
        idsToForward.forEach { buffer.put(it.base64()) }
        val serviceData = buffer.array().copyOf(buffer.position())

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(uuid, serviceData)
            .build()

        adapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        advertiseCallback?.let {
            adapter.bluetoothLeAdvertiser?.stopAdvertising(it)
            advertiseCallback = null
        }
    }

    private suspend fun CoroutineScope.runScanLoop() {
        while (isActive) {
            startScan()
            delay(scanTimeout.toLong())
            stopScan()
            delay(500)
        }
    }

    private suspend fun CoroutineScope.runAdvertisingLoop() {
        while (isActive) {
            startAdvertising()
            delay(advertiseTimeout.toLong())
            stopAdvertising()
            delay(500)
        }
    }
}

@Composable
fun BleCounterPermissionsUi() {
    val context = LocalContext.current
    val permissionsManager = context.app.permissionsManager
    val permissionsState by permissionsManager.state.collectAsState()
    var locationExplanationDialog by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "لطفا بلوتوث را روشن کنید و به لب پنجره بروید. از بلوتوث برای شمارش تعداد مشارکت‌کنندگان در نزدیکی شما استفاده می‌شود. در آینده می‌تواند برای پخش سریع اخبار و اطلاعیه‌های مهم در زمان قطعی اینترنت نیز کاربردی باشد.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        if (!permissionsState.bleConnect || !permissionsState.bleAdvertise || !permissionsState.bleScan) {
            Text(
                text = "اپلیکیشن برای استفاده از بلوتوث نیاز به مجوز شما دارد.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = { permissionsManager.askBluetooth() }) {
                Text(text = "مجوز سیستم بلوتوث")
            }
        } else if (!permissionsState.bleEnabled) {
            Text(
                text = "برای شمارش مشارکت‌کنندگان در نزدیکی شما لازم است بلوتوث را روشن کنید.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.size(16.dp))
            Button(onClick = { permissionsManager.enableBluetooth(context) }) {
                Text(text = "روشن کردن بلوتوث")
            }
        } else if (!permissionsState.location) {
            Text(
                text = "متأسفانه از اندروید 6 به بعد برای اسکن بلوتوث از نوع BLE، مجوز و روشن بودن سرویس مکان‌یاب نیز لازم است. این اَپلیکیشن به هیچ عنوان از GPS استفاده نمی‌کند و کاری به مکان شما ندارد.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { locationExplanationDialog = true }) {
                Text(
                    text = "چرا سرویس مکان‌یاب لازم است؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = { permissionsManager.askLocation() }) {
                Text(text = "مجوز سرویس مکان یاب")
            }
        } else if (!permissionsState.locationEnabled) {
            Text(
                text = "متأسفانه از اندروید 6 به بعد برای اسکن بلوتوث از نوع BLE، مجوز و روشن بودن سرویس مکان‌یاب نیز لازم است. این اَپلیکیشن به هیچ عنوان از GPS استفاده نمی‌کند و کاری به مکان شما ندارد.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { locationExplanationDialog = true }) {
                Text(
                    text = "چرا سرویس مکان‌یاب لازم است؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = { permissionsManager.enableLocation(context) }) {
                Text(text = "روشن کردن سیستم مکان یابی")
            }
        }
    }

    if (locationExplanationDialog) {
        LocationExplanationDialog(onClose = { locationExplanationDialog = false })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LocationExplanationDialog(onClose: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        onDismissRequest = { onClose() },
        confirmButton = {
            TextButton(onClick = { onClose() }) {
                Text("باشه")
            }
        },
        title = { Text(text = "چرا سیستم مکان یابی لازمه؟", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = "از تکنولوژی Bluetooth Low Energy علاوه‌بر ایجاد ارتباطِ بی‌سیم بین دستگا‌های نزدیک، می‌توان برای پیدا کردن موقعیت مکانی نیز استفاده کرد. برای مثال وقتی داخل یک ساختمان بزرگ هستید، مثل بیمارستان یا فرودگاه، به دلیل بسته بودن سقف، امواج ماهواره‌های موقعیت‌یابی نمی‌توانند وارد ساختمان شوند. به همین دلیل برای موقعیت‌یابی داخل ساختمان، از تکنولوژی‌های دیگری مانند بلوتوث و وای‌فای استفاده می‌شود. امروزه استفاده از بلوتوث برای موقعیت‌یابی آنقدر فراگیر شده است که اندروید و ایفون هر دو برای اسکن بلوتوث دسترسی به سرویس مکان‌یابی را اجباری کرده‌اند. البته این اپلیکیشن با مکان جغرافیایی شما کاری ندارد و از بلوتوث فقط برای ارتباط برقرار کردن با افرادِ نزدیکِ شما استفاده می‌کند.")
                Spacer(modifier = Modifier.size(4.dp))
                TextButton(
                    onClick = {
                        openWeb(
                            context,
                            "https://www.inpixon.com/technology/standards/bluetooth-low-energy"
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("اطلاعات بیشتر (انگلیسی)", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(
                    onClick = {
                        openWeb(
                            context,
                            "https://issuetracker.google.com/issues/37065090"
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Column {
                        Text("صفحه ی این موضوع در موضوع-نگر گوگل", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.size(2.dp))
                        Text(
                            "شاید بدون فیلترشکن باز نشود!",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
    )
}