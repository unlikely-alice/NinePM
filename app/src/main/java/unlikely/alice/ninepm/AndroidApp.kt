package unlikely.alice.ninepm

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

class AndroidApp : Application() {
    val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val permissionsManager by lazy { PermissionsManager(this) }
    val bleCounter by lazy { BleCounter(this) }
    val httpClient by lazy { HttpClient(OkHttp) }

    override fun onCreate() {
        super.onCreate()
        prepareAppPreferences(this)
        createAlarmNotificationChannel(this)
    }
}