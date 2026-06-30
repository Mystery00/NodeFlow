package app.mystery0.nodeflow.feature.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.loginCompleted) {
        if (state.loginCompleted) {
            onEvent(AuthUiEvent.LoginResultConsumed)
            onLoginSuccess()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.twoFactorChallenge == null) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { onEvent(AuthUiEvent.UsernameChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户名或邮箱") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onEvent(AuthUiEvent.PasswordChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                CaptchaContent(
                    state = state,
                    onRefresh = { onEvent(AuthUiEvent.RefreshChallenge) },
                )
                OutlinedTextField(
                    value = state.captcha,
                    onValueChange = { onEvent(AuthUiEvent.CaptchaChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("验证码") },
                    singleLine = true,
                )
            } else {
                TwoFactorContent(
                    state = state,
                    onCodeChanged = { onEvent(AuthUiEvent.TwoFactorCodeChanged(it)) },
                    onRestart = { onEvent(AuthUiEvent.RefreshChallenge) },
                )
            }
            state.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { onEvent(AuthUiEvent.Submit) },
                enabled = state.canSubmit && !state.isLoadingChallenge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                val buttonText = if (state.twoFactorChallenge == null) {
                    if (state.isSubmitting) "登录中" else "登录"
                } else {
                    if (state.isSubmitting) "验证中" else "验证并登录"
                }
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun TwoFactorContent(
    state: AuthUiState,
    onCodeChanged: (String) -> Unit,
    onRestart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = state.twoFactorChallenge?.title ?: "两步验证",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "请输入身份验证器中的 6 位验证码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.twoFactorCode,
            onValueChange = onCodeChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("两步验证码") },
            singleLine = true,
        )
        OutlinedButton(
            onClick = onRestart,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("重新获取登录验证码")
        }
    }
}

@Composable
private fun CaptchaContent(
    state: AuthUiState,
    onRefresh: () -> Unit,
) {
    val imageBitmap = remember(state.challenge?.captchaImageBytes) {
        state.challenge?.captchaImageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "验证码",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.isLoadingChallenge && !state.isSubmitting,
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("刷新")
            }
        }
        if (state.isLoadingChallenge) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
            }
        } else if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "验证码图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = "验证码加载失败，请刷新重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
