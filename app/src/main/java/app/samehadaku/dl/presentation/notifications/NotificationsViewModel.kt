package app.samehadaku.dl.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.notifications.NotificationRepository
import app.samehadaku.dl.domain.model.AppNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val items: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
) {
    val unreadCount: Int get() = items.count { !it.read }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.notifications.collect { items ->
                _state.update { it.copy(items = items, loading = false) }
            }
        }
    }

    fun markRead(item: AppNotification) {
        if (item.read) return
        viewModelScope.launch { runCatching { repository.markRead(item.id) } }
    }

    fun markAllRead() {
        viewModelScope.launch { runCatching { repository.markAllRead() } }
    }

    fun clearAll() {
        viewModelScope.launch { runCatching { repository.clear() } }
    }
}
