package app.mystery0.nodeflow.feature.editor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.TitleChanged -> _uiState.update { it.copy(title = event.value) }
            is EditorUiEvent.ContentChanged -> _uiState.update { it.copy(content = event.value) }
        }
    }
}
