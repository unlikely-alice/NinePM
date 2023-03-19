package unlikely.alice.ninepm

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "app-preferences")

object PreferenceKeys {
    val BLUETOOTH_ID = stringPreferencesKey("bluetooth-id")
    val ALARM_ENABLED = booleanPreferencesKey("alarm-enabled")
}

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val initialState = remember {
        runBlocking { context.preferences.data.first()[key] ?: defaultValue }
    }
    val state = remember {
        context.preferences.data
            .map {
                it[key] ?: defaultValue
            }
    }.collectAsState(initial = initialState)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.preferences.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

// Call this early to prevent ANR, because I'm synchronously reading state in rememberAppData to prevent
// UI changing suddenly after 1ms.
fun prepareAppPreferences(context: Context) {
    (MainScope() + Dispatchers.IO).launch {
        context.preferences.data.first()
        // TODO: You should also handle IOExceptions here.
    }
}