package unlikely.alice.ninepm

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@SuppressLint("MissingPermission")
class PermissionsManager(private val app: AndroidApp) {
    private val _state = MutableStateFlow(checkPermissions())
    val state: StateFlow<PermissionsState> = _state

    init {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION)
        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                _state.value = checkPermissions()
            }
        }
        app.registerReceiver(broadcast, intentFilter)
    }

    private fun checkPermissions() = PermissionsState(
        bleAdvertise = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        },

        bleScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        },

        bleConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        },

        location = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED,

        bleEnabled = app.bluetoothManager.adapter.isEnabled,

        locationEnabled = LocationManagerCompat.isLocationEnabled(app.locationManager),
    )

    lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    fun onCreateActivity(activity: ComponentActivity) {
        // TODO: Handle permissions denied because dialog will not be shown again
        // https://stackoverflow.com/questions/32347532/android-m-permissions-confused-on-the-usage-of-shouldshowrequestpermissionrati
        requestPermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            _state.value = checkPermissions()
        }
    }

    fun askBluetooth() {
        val requestList = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!state.value.bleScan) requestList.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!state.value.bleAdvertise) requestList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (!state.value.bleConnect) requestList.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissionsLauncher.launch(requestList.toTypedArray())
    }

    fun askLocation() {
        val requestList = mutableListOf<String>()
        if (!state.value.location) requestList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!state.value.location) requestList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        requestPermissionsLauncher.launch(requestList.toTypedArray())
    }

    fun enableBluetooth(context: Context) {
        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    fun enableLocation(context: Context) {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}

class PermissionsState(
    val bleAdvertise: Boolean,
    val bleScan: Boolean,
    val bleConnect: Boolean,
    val location: Boolean,
    val bleEnabled: Boolean,
    val locationEnabled: Boolean
) {
    val canStartBleCounter = bleAdvertise && bleScan && bleConnect && location && bleEnabled && locationEnabled
}