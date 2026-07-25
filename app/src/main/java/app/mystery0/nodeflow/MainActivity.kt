package app.mystery0.nodeflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mystery0.nodeflow.core.designsystem.component.LocalCustomImageHosts
import app.mystery0.nodeflow.core.designsystem.component.LocalMemberTags
import app.mystery0.nodeflow.core.designsystem.component.effectiveImageHosts
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.link.V2exLink
import app.mystery0.nodeflow.core.link.V2exLinkParser
import app.mystery0.nodeflow.navigation.NodeFlowNavHost
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<V2exLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink = parseDeepLink(intent)
        setContent {
            val appViewModel: AppViewModel = koinViewModel()
            val settings = appViewModel.settings.collectAsStateWithLifecycle()
            val customImageHosts = remember(settings.value.customImageHosts) {
                effectiveImageHosts(settings.value.customImageHosts)
            }
            val memberTags = appViewModel.memberTags.collectAsStateWithLifecycle()
            NodeFlowTheme(settings = settings.value) {
                CompositionLocalProvider(
                    LocalCustomImageHosts provides customImageHosts,
                    LocalMemberTags provides memberTags.value,
                ) {
                    NodeFlowNavHost(
                        settings = settings.value,
                        deepLink = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseDeepLink(intent)?.let { pendingDeepLink = it }
    }

    private fun parseDeepLink(intent: Intent?): V2exLink? {
        val uri = intent?.data ?: return null
        return V2exLinkParser.parse(uri.toString())
    }
}
