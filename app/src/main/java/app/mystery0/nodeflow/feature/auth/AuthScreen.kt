package app.mystery0.nodeflow.feature.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mystery0.nodeflow.R

// 用户名和图片验证码沿用 ASCII 输入规则；密码必须保留用户输入的原始字符。
internal fun asciiCredentialInput(input: String): String = input.filter { it in '!'..'~' }

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTwoFactor = state.twoFactorChallenge != null
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val captchaBitmap = remember(state.challenge?.captchaImageBytes) {
        state.challenge?.captchaImageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val canSubmit = state.canSubmit && (isTwoFactor || captchaBitmap != null)
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onEvent(AuthUiEvent.Submit)
        }
    }
    LaunchedEffect(isTwoFactor) {
        // 新阶段先展示账号和说明，用户点击输入框后再打开数字键盘。
        focusManager.clearFocus()
        scrollState.scrollTo(0)
    }
    LaunchedEffect(state.loginCompleted) {
        if (state.loginCompleted) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onEvent(AuthUiEvent.LoginResultConsumed)
            onLoginSuccess()
        }
    }
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isTwoFactor) R.string.auth_two_factor else R.string.auth_login)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.auth_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).consumeWindowInsets(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxSize()
                    .verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AuthHeading(state)
                if (isTwoFactor) {
                    TwoFactorContent(state, onEvent, submit)
                } else {
                    CredentialFields(state, captchaBitmap, onEvent, submit)
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    state.errorMessage?.let { AuthErrorMessage(it) }
                    AuthSubmitButton(state, canSubmit, submit)
                    if (!isTwoFactor) {
                        Text(
                            stringResource(R.string.auth_credentials_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthHeading(state: AuthUiState) {
    val isTwoFactor = state.twoFactorChallenge != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (isTwoFactor) R.string.auth_two_factor_heading else R.string.auth_welcome),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        if (isTwoFactor) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.auth_verified), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            state.username.takeIf { it.isNotBlank() }?.let { username ->
                Text(
                    stringResource(R.string.auth_verifying_account, username.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                stringResource(R.string.auth_login_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CredentialFields(state: AuthUiState, captchaBitmap: ImageBitmap?, onEvent: (AuthUiEvent) -> Unit, onSubmit: () -> Unit) {
    val passwordFocus = remember { FocusRequester() }
    val captchaFocus = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }
    val captchaReady = captchaBitmap != null && !state.isLoadingChallenge
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.username,
            onValueChange = { onEvent(AuthUiEvent.UsernameChanged(asciiCredentialInput(it))) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            label = { Text(stringResource(R.string.auth_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = { onEvent(AuthUiEvent.PasswordChanged(it)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).semantics { contentType = ContentType.Password },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { if (captchaReady) captchaFocus.requestFocus() }),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.isSubmitting) {
                    Icon(
                        if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password),
                    )
                }
            },
        )
        CaptchaContent(state, captchaBitmap) { onEvent(AuthUiEvent.RefreshChallenge) }
        OutlinedTextField(
            value = state.captcha,
            onValueChange = { onEvent(AuthUiEvent.CaptchaChanged(asciiCredentialInput(it))) },
            enabled = !state.isSubmitting && captchaReady,
            modifier = Modifier.fillMaxWidth().focusRequester(captchaFocus),
            label = { Text(stringResource(R.string.auth_image_code)) },
            placeholder = { Text(stringResource(R.string.auth_captcha_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
    }
}

@Composable
private fun TwoFactorContent(state: AuthUiState, onEvent: (AuthUiEvent) -> Unit, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.auth_two_factor_description), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = state.twoFactorCode,
            onValueChange = { onEvent(AuthUiEvent.TwoFactorCodeChanged(it)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_two_factor_code)) },
            placeholder = { Text(stringResource(R.string.auth_two_factor_hint), style = MaterialTheme.typography.bodyLarge) },
            supportingText = { Text(stringResource(R.string.auth_two_factor_help)) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 4.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
    }
}

@Composable
private fun CaptchaContent(state: AuthUiState, image: ImageBitmap?, onRefresh: () -> Unit) {
    val refreshDescription = stringResource(R.string.auth_captcha_refresh_description)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.auth_image_code), style = MaterialTheme.typography.titleSmall)
            TextButton(
                onClick = onRefresh,
                enabled = !state.isLoadingChallenge && !state.isSubmitting,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = refreshDescription },
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (image == null && !state.isLoadingChallenge) R.string.auth_captcha_retry else R.string.auth_captcha_refresh),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().height(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    state.isLoadingChallenge -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.auth_captcha_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                    image != null -> Image(image, contentDescription = stringResource(R.string.auth_captcha_image), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else -> Text(stringResource(R.string.auth_captcha_failed), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AuthErrorMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthSubmitButton(state: AuthUiState, canSubmit: Boolean, onSubmit: () -> Unit) {
    val colors = if (state.isSubmitting) {
        ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.primary, disabledContentColor = MaterialTheme.colorScheme.onPrimary)
    } else ButtonDefaults.buttonColors()
    Button(onClick = onSubmit, enabled = canSubmit, colors = colors, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
        }
        Text(
            stringResource(when {
                state.twoFactorChallenge != null && state.isSubmitting -> R.string.auth_verifying
                state.twoFactorChallenge != null -> R.string.auth_verify_and_login
                state.isSubmitting -> R.string.auth_logging_in
                else -> R.string.auth_login
            }),
            modifier = Modifier.padding(start = if (state.isSubmitting) 8.dp else 0.dp),
        )
    }
}
