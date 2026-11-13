package app.mystery0.nodeflow.feature.auth

import android.content.ClipboardManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mystery0.nodeflow.R

private val FieldShape = RoundedCornerShape(12.dp)
private const val STEP_TRANSITION_MS = 260
private const val TWO_FACTOR_LENGTH = 6

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
    val handleBack = {
        if (isTwoFactor) {
            if (!state.isSubmitting) {
                onEvent(AuthUiEvent.BackToCredentials)
            }
        } else {
            onBackClick()
        }
    }
    BackHandler(enabled = isTwoFactor) {
        handleBack()
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
                title = {
                    val showTitle = isTwoFactor || scrollState.value > 0
                    if (showTitle) {
                        Text(stringResource(if (isTwoFactor) R.string.auth_two_factor else R.string.auth_login))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
                        enabled = !isTwoFactor || !state.isSubmitting,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.auth_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                AnimatedContent(
                    targetState = isTwoFactor,
                    transitionSpec = {
                        val direction = if (targetState) 1 else -1
                        (slideInHorizontally(animationSpec = tween(STEP_TRANSITION_MS)) { it / 3 * direction } +
                            fadeIn(animationSpec = tween(STEP_TRANSITION_MS))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(STEP_TRANSITION_MS)) { -it / 3 * direction } +
                                fadeOut(animationSpec = tween(STEP_TRANSITION_MS / 2)))
                    },
                    label = "auth_step_transition",
                ) { twoFactorStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (twoFactorStep) {
                            TwoFactorHeading(state = state, onEvent = onEvent)
                            TwoFactorContent(state = state, onEvent = onEvent, onSubmit = submit)
                        } else {
                            CredentialHeading()
                            CredentialFields(
                                state = state,
                                captchaBitmap = captchaBitmap,
                                onEvent = onEvent,
                                onSubmit = submit,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.errorMessage?.let { AuthErrorMessage(it) }
                            AuthSubmitButton(state = state, canSubmit = canSubmit, onSubmit = submit)
                            if (!twoFactorStep) {
                                AuthTrustFooter()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthStepBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = FieldShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CredentialHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthStepBadge(
                icon = Icons.Outlined.Forum,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.auth_welcome),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            stringResource(R.string.auth_login_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TwoFactorHeading(
    state: AuthUiState,
    onEvent: (AuthUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuthStepBadge(
                    icon = Icons.Outlined.VerifiedUser,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.auth_two_factor_heading),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.auth_two_factor_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VerifiedAccountCard(
            username = state.username.trim(),
            enabled = !state.isSubmitting,
            onSwitchAccount = { onEvent(AuthUiEvent.BackToCredentials) },
        )
    }
}

@Composable
private fun VerifiedAccountCard(
    username: String,
    enabled: Boolean,
    onSwitchAccount: () -> Unit,
) {
    Surface(
        shape = FieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (username.isNotEmpty()) {
                    Text(
                        username,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(R.string.auth_verified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = onSwitchAccount,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.auth_switch_account))
            }
        }
    }
}

@Composable
private fun CredentialFields(
    state: AuthUiState,
    captchaBitmap: ImageBitmap?,
    onEvent: (AuthUiEvent) -> Unit,
    onSubmit: () -> Unit,
) {
    val passwordFocus = remember { FocusRequester() }
    val captchaFocus = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }
    val captchaReady = captchaBitmap != null && !state.isLoadingChallenge
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.username,
            onValueChange = { onEvent(AuthUiEvent.UsernameChanged(asciiCredentialInput(it))) },
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username },
            shape = FieldShape,
            label = { Text(stringResource(R.string.auth_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            trailingIcon = if (state.username.isNotEmpty() && !state.isSubmitting) {
                {
                    IconButton(onClick = { onEvent(AuthUiEvent.UsernameChanged("")) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.auth_clear_input),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                null
            },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = { onEvent(AuthUiEvent.PasswordChanged(it)) },
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .semantics { contentType = ContentType.Password },
            shape = FieldShape,
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
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
        CaptchaFieldRow(
            state = state,
            captchaBitmap = captchaBitmap,
            captchaReady = captchaReady,
            captchaFocus = captchaFocus,
            onCaptchaChange = { onEvent(AuthUiEvent.CaptchaChanged(asciiCredentialInput(it))) },
            onRefresh = { onEvent(AuthUiEvent.RefreshChallenge) },
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun CaptchaFieldRow(
    state: AuthUiState,
    captchaBitmap: ImageBitmap?,
    captchaReady: Boolean,
    captchaFocus: FocusRequester,
    onCaptchaChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSubmit: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactStacked = maxWidth < 340.dp || fontScale >= 1.5f
        if (compactStacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptchaImageBox(
                    state = state,
                    image = captchaBitmap,
                    onRefresh = onRefresh,
                    compact = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                )
                CaptchaTextField(
                    value = state.captcha,
                    enabled = !state.isSubmitting && captchaReady,
                    focusRequester = captchaFocus,
                    onValueChange = onCaptchaChange,
                    onSubmit = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CaptchaTextField(
                    value = state.captcha,
                    enabled = !state.isSubmitting && captchaReady,
                    focusRequester = captchaFocus,
                    onValueChange = onCaptchaChange,
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
                CaptchaImageBox(
                    state = state,
                    image = captchaBitmap,
                    onRefresh = onRefresh,
                    compact = true,
                    // 与 OutlinedTextField 顶部浮动标签偏移对齐。
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(148.dp)
                        .height(56.dp),
                )
            }
        }
    }
}

@Composable
private fun CaptchaTextField(
    value: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier.focusRequester(focusRequester),
        shape = FieldShape,
        label = { Text(stringResource(R.string.auth_image_code)) },
        placeholder = { Text(stringResource(R.string.auth_captcha_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}

@Composable
private fun CaptchaImageBox(
    state: AuthUiState,
    image: ImageBitmap?,
    onRefresh: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val refreshDescription = stringResource(R.string.auth_captcha_refresh_description)
    val isFailed = image == null && !state.isLoadingChallenge
    val enabled = !state.isLoadingChallenge && !state.isSubmitting
    val containerColor = when {
        image != null -> Color.White
        isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (isFailed) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onRefresh,
        enabled = enabled,
        shape = FieldShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = refreshDescription
            role = Role.Button
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                state.isLoadingChallenge -> Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(if (compact) R.string.auth_captcha_loading_short else R.string.auth_captcha_loading),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                image != null -> {
                    Image(
                        image,
                        contentDescription = stringResource(R.string.auth_captcha_image),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(20.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
                else -> Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(if (compact) R.string.auth_captcha_tap_retry else R.string.auth_captcha_failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TwoFactorContent(
    state: AuthUiState,
    onEvent: (AuthUiEvent) -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val hasError = state.errorMessage != null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.twoFactorCode,
            onValueChange = { onEvent(AuthUiEvent.TwoFactorCodeChanged(it)) },
            enabled = !state.isSubmitting,
            isError = hasError,
            modifier = Modifier.fillMaxWidth(),
            shape = FieldShape,
            label = { Text(stringResource(R.string.auth_two_factor_code)) },
            placeholder = {
                Text(
                    stringResource(R.string.auth_two_factor_hint),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                letterSpacing = 6.sp,
                fontFeatureSettings = "tnum",
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                if (state.twoFactorCode.isEmpty()) {
                    TextButton(
                        enabled = !state.isSubmitting,
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            val pasted = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!pasted.isNullOrEmpty()) {
                                onEvent(AuthUiEvent.TwoFactorCodeChanged(pasted))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.auth_paste))
                    }
                } else if (!state.isSubmitting) {
                    IconButton(onClick = { onEvent(AuthUiEvent.TwoFactorCodeChanged("")) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.auth_clear_input),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )
        TwoFactorSlotProgress(
            filledCount = state.twoFactorCode.length,
            isError = hasError,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.auth_two_factor_help),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.auth_two_factor_progress, state.twoFactorCode.length),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (state.twoFactorCode.length == TWO_FACTOR_LENGTH) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TwoFactorSlotProgress(
    filledCount: Int,
    isError: Boolean,
) {
    val activeColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(TWO_FACTOR_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        color = if (index < filledCount) activeColor else inactiveColor,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun AuthErrorMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = FieldShape,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthSubmitButton(state: AuthUiState, canSubmit: Boolean, onSubmit: () -> Unit) {
    val colors = if (state.isSubmitting) {
        ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    Button(
        onClick = onSubmit,
        enabled = canSubmit,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        }
        Text(
            stringResource(
                when {
                    state.twoFactorChallenge != null && state.isSubmitting -> R.string.auth_verifying
                    state.twoFactorChallenge != null -> R.string.auth_verify_and_login
                    state.isSubmitting -> R.string.auth_logging_in
                    else -> R.string.auth_login
                },
            ),
            modifier = Modifier.padding(start = if (state.isSubmitting) 8.dp else 0.dp),
        )
    }
}

@Composable
private fun AuthTrustFooter() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.auth_credentials_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
