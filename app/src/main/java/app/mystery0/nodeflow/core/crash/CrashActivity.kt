package app.mystery0.nodeflow.core.crash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.AppSettings

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT)
            .orEmpty()
            .ifBlank { "未能捕获到崩溃日志" }
        setContent {
            // 崩溃页运行在独立进程且不初始化 Koin，主题只能用默认设置
            NodeFlowTheme(settings = AppSettings()) {
                CrashScreen(
                    report = report,
                    onShare = { shareReport(report) },
                    onRestart = { restartApp() },
                )
            }
        }
    }

    private fun shareReport(report: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NodeFlow 崩溃日志")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(sendIntent, "分享崩溃日志"))
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launchIntent)
        }
        finishAffinity()
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val EXTRA_REPORT = "extra_crash_report"

        fun newIntent(context: Context, report: String): Intent =
            Intent(context, CrashActivity::class.java)
                .putExtra(EXTRA_REPORT, report)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashScreen(
    report: String,
    onShare: () -> Unit,
    onRestart: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("应用发生崩溃") }) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "很抱歉，NodeFlow 遇到问题意外退出了。你可以把下面的日志分享给开发者帮助定位问题。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestart,
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    Text("重启应用", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onShare,
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text("分享日志", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
