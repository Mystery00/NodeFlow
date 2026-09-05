package app.mystery0.nodeflow.feature.account

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    onEvent: (AccountUiEvent) -> Unit,
    onSettingsClick: () -> Unit,
    onLoginClick: () -> Unit,
    onFavoriteTopicsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLogoutConfirmation by rememberSaveable(state.session.username) { mutableStateOf(false) }
    LaunchedEffect(state.isLoggedIn) {
        if (!state.isLoggedIn) showLogoutConfirmation = false
    }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onEvent(AccountUiEvent.ToastShown)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { onEvent(AccountUiEvent.Refresh) }) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.favorites_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoggedIn && state.user != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AccountProfileHeader(user = state.user)
                state.overview?.let { overview ->
                    AccountOverviewCard(
                        overview = overview,
                        isCheckingIn = state.isCheckingIn,
                        onCheckInClick = { onEvent(AccountUiEvent.CheckIn) },
                    )
                }
                AccountMenu(true, onFavoriteTopicsClick, onSettingsClick)
                OutlinedButton(
                    onClick = { showLogoutConfirmation = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.account_logout))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Box(Modifier.weight(1f)) {
                    when {
                        !state.isLoggedIn -> SignedOutContent(onLoginClick)
                        state.isLoading -> LoadingContent()
                        state.errorMessage != null -> ErrorContent(
                            message = state.errorMessage,
                            onRetry = { onEvent(AccountUiEvent.Retry) },
                        )
                        else -> EmptyContent(message = stringResource(R.string.account_no_profile))
                    }
                }
                AccountMenu(false, onFavoriteTopicsClick, onSettingsClick)
            }
        }
    }
    if (showLogoutConfirmation && state.isLoggedIn) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text(stringResource(R.string.account_logout_title)) },
            text = { Text(stringResource(R.string.account_logout_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirmation = false
                    onEvent(AccountUiEvent.Logout)
                }) {
                    Text(stringResource(R.string.account_logout_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}

@Composable
private fun SignedOutContent(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.account_signed_out), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.account_login_description),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLoginClick) { Text(stringResource(R.string.favorites_login)) }
    }
}

@Composable
private fun AccountMenu(showFavorites: Boolean, onFavoriteTopicsClick: () -> Unit, onSettingsClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            if (showFavorites) {
                AccountMenuItem(stringResource(R.string.favorite_topics), Icons.Outlined.StarBorder, onFavoriteTopicsClick)
            }
            AccountMenuItem(stringResource(R.string.account_settings), Icons.Outlined.Settings, onSettingsClick)
        }
    }
}

@Composable
private fun AccountMenuItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
