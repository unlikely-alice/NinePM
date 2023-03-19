package unlikely.alice.ninepm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import unlikely.alice.ninepm.ui.theme.NewIranTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app.permissionsManager.onCreateActivity(this)

        setContent {
            NewIranTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ActivityUi()
                }
            }
        }
    }

    @Composable
    fun ActivityUi() {
        MainScreen()
    }

    override fun onResume() {
        super.onResume()
        turnScreenOnAndKeyguardOff(this)
    }
}