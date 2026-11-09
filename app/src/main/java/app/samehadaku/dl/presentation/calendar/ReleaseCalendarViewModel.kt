package app.samehadaku.dl.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.domain.model.ReleaseScheduleItem
import app.samehadaku.dl.domain.repository.SiteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReleaseCalendarUiState(
    val loading: Boolean = true,
    val items: List<ReleaseScheduleItem> = emptyList(),
    val error: String? = null,
    val needsVerification: Boolean = false,
)

@HiltViewModel
class ReleaseCalendarViewModel @Inject constructor(
    private val repository: SiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReleaseCalendarUiState())
    val state: StateFlow<ReleaseCalendarUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, needsVerification = false) }
            try {
                _state.update {
                    it.copy(loading = false, items = repository.loadReleaseSchedule())
                }
            } catch (verification: NeedsVerificationException) {
                _state.update {
                    it.copy(loading = false, needsVerification = true)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Jadwal anime belum bisa dimuat.",
                    )
                }
            }
        }
    }
}
