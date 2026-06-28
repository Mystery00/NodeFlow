package app.mystery0.nodeflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.navigation.NodeFlowNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings = appViewModel.settings.collectAsStateWithLifecycle()
            NodeFlowTheme(settings = settings.value) {
                NodeFlowNavHost()
            }
        }
    }
}
