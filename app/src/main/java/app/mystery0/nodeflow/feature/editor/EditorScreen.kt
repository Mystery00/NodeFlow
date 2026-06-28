package app.mystery0.nodeflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditorScreen(
    state: EditorUiState,
    onEvent: (EditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("编辑器预留", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = state.title,
            onValueChange = { onEvent(EditorUiEvent.TitleChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("标题") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.content,
            onValueChange = { onEvent(EditorUiEvent.ContentChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("正文") },
        )
        Text("TODO：登录、once 和发布接口确认后接入发帖/回复。")
    }
}
