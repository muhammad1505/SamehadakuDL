package app.samehadaku.dl.presentation.calendar

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.repository.AnimeCoverRepository
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
    val covers: Map<String, Bitmap?> = emptyMap(),
    val error: String? = null,
    val needsVerification: Boolean = false,
)

@HiltViewModel
class ReleaseCalendarViewModel @Inject constructor(
    private val repository: SiteRepository,
    private val coverRepository: AnimeCoverRepository,
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
                val items = repository.loadReleaseSchedule()
                _state.update { it.copy(loading = false, items = items, covers = emptyMap()) }
                items.mapNotNull { item -> item.coverUrl?.let { url -> item to url } }
                    .distinctBy { (_, url) -> url }
                    .forEach { (item, url) ->
                        launch {
                            val cover = coverRepository.loadImage(url, item.url)
                            _state.update { current ->
                                current.copy(covers = current.covers + (url to cover))
                            }
                        }
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
