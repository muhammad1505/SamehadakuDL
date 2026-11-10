package app.samehadaku.dl.presentation.calendar

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.ReleaseScheduleItem
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.pressable
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPink
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable
fun ReleaseCalendarScreen(
    onBack: () -> Unit,
    onOpenCloudflare: () -> Unit,
    onOpenDetail: () -> Unit,
    viewModel: ReleaseCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dates = remember(month) { monthDates(month) }
    val scheduleByDate = remember(month, state.items) {
        state.items.groupByWeekday(month)
    }
    val selectedItems = scheduleByDate[selectedDate].orEmpty()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ReleaseCalendarEvent.OpenDetail -> onOpenDetail()
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    ScreenScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ScreenTopBar(
                title = "Jadwal Anime",
                subtitle = "Perkiraan rilis mingguan dari Samehadaku",
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang jadwal",
                        onClick = viewModel::load,
                        tint = TextSecondary,
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandPrimary, strokeWidth = 3.dp)
            }
            state.needsVerification -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                InfoBanner(
                    title = "Verifikasi diperlukan",
                    message = "Selesaikan verifikasi Cloudflare untuk membaca jadwal rilis.",
                    icon = Icons.Rounded.CloudOff,
                    accent = BrandGold,
                    actionLabel = "Buka verifikasi",
                    onAction = onOpenCloudflare,
                )
            }
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                InfoBanner(
                    title = "Jadwal belum tersedia",
                    message = state.error.orEmpty(),
                    icon = Icons.Rounded.CalendarMonth,
                    accent = Danger,
                    actionLabel = "Coba lagi",
                    onAction = viewModel::load,
                )
            }
            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "Jadwal belum terbaca",
                    message = "Coba muat ulang beberapa saat lagi.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(13.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = BrandMint,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "Waktu di situs bisa berubah jika anime mengalami delay atau libur.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (state.actionNeedsVerification) {
                    item {
                        InfoBanner(
                            title = "Verifikasi Cloudflare diperlukan",
                            message = "Selesaikan verifikasi untuk mengubah status Langganan.",
                            icon = Icons.Rounded.CloudOff,
                            accent = BrandGold,
                            actionLabel = "Buka verifikasi",
                            onAction = onOpenCloudflare,
                        )
                    }
                }
                item {
                    MonthCalendar(
                        month = month,
                        selectedDate = selectedDate,
                        dates = dates,
                        scheduleByDate = scheduleByDate,
                        onPrevious = {
                            month = month.minusMonths(1)
                            selectedDate = month.atDay(1)
                        },
                        onNext = {
                            month = month.plusMonths(1)
                            selectedDate = month.atDay(1)
                        },
                        onSelectDate = { date ->
                            selectedDate = date
                        },
                    )
                }
                item {
                    Text(
                        text = selectedDate.format(DATE_FORMATTER),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (selectedItems.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.CalendarMonth,
                            title = "Tidak ada jadwal",
                            message = "Belum ada anime yang dijadwalkan pada hari ini.",
                        )
                    }
                } else {
                    items(
                        selectedItems,
                        key = { item -> "${selectedDate}-${releaseScheduleKey(item)}" },
                    ) { item ->
                        val key = releaseScheduleKey(item)
                        val slug = item.url?.let { UrlUtils.lastPathSegment(it) }
                        ScheduleRow(
                            item = item,
                            cover = state.covers[key],
                            rating = item.rating ?: state.ratings[key],
                            favorite = slug != null && slug in state.subscribedSlugs,
                            busy = state.favoriteBusyUrl != null &&
                                state.favoriteBusyUrl == item.url,
                            onOpen = item.url?.let { { viewModel.openDetail(item) } },
                            onToggleFavorite = slug?.let {
                                { viewModel.toggleFavorite(item) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    dates: List<LocalDate?>,
    scheduleByDate: Map<LocalDate, List<ReleaseScheduleItem>>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = "Bulan sebelumnya",
                onClick = onPrevious,
                size = 30.dp,
                iconSize = 18.dp,
                tint = TextSecondary,
            )
            Text(
                text = month.format(MONTH_FORMATTER),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            GlassIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "Bulan berikutnya",
                onClick = onNext,
                size = 30.dp,
                iconSize = 18.dp,
                tint = TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        dates.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { date ->
                    CalendarDateCell(
                        date = date,
                        month = month,
                        selected = date == selectedDate,
                        hasSchedule = date != null && scheduleByDate[date].orEmpty().isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { date?.let(onSelectDate) },
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarDateCell(
    date: LocalDate?,
    month: YearMonth,
    selected: Boolean,
    hasSchedule: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .pressable(
                enabled = date != null,
                pressedScale = 0.92f,
                onClick = onClick,
            )
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandPrimary.copy(alpha = 0.14f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            val currentMonth = date.month == month.month
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = when {
                        selected -> BrandPrimary
                        !currentMonth -> TextMuted.copy(alpha = 0.35f)
                        else -> TextPrimary
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else null,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(if (hasSchedule) 5.dp else 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (hasSchedule) BrandMint else Color.Transparent),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ScheduleRow(
    item: ReleaseScheduleItem,
    cover: Bitmap?,
    rating: String?,
    favorite: Boolean,
    busy: Boolean,
    onOpen: (() -> Unit)?,
    onToggleFavorite: (() -> Unit)?,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScheduleCover(cover = cover, title = item.title)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 24.dp,
                    ),
                )
                Text(
                    text = listOfNotNull(item.time, item.episode).joinToString(" • ")
                        .ifBlank { "Jadwal mingguan" },
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            rating?.let {
                Spacer(Modifier.width(8.dp))
                StatusPill(text = "★ $it", color = BrandGold)
            }
            item.status?.let { status ->
                Spacer(Modifier.width(7.dp))
                StatusPill(
                    text = status,
                    color = if (status.equals("Delay", true)) BrandGold else Danger,
                )
            }
            if (onToggleFavorite != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .pressable(
                            enabled = !busy,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = if (favorite) {
                                "Hapus ${item.title} dari Langganan"
                            } else {
                                "Tambahkan ${item.title} ke Langganan"
                            },
                            pressedScale = 0.88f,
                            onClick = onToggleFavorite,
                        )
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (favorite) {
                                BrandPink.copy(alpha = 0.16f)
                            } else {
                                Color.White.copy(alpha = 0.06f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = BrandPink,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (favorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = null,
                            tint = if (favorite) BrandPink else TextMuted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCover(cover: Bitmap?, title: String) {
    val shape = RoundedCornerShape(10.dp)
    if (cover != null) {
        Image(
            bitmap = cover.asImageBitmap(),
            contentDescription = "Cover $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 42.dp, height = 56.dp)
                .clip(shape),
        )
        return
    }
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 56.dp)
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        BrandPrimary.copy(alpha = 0.22f),
                        BrandPink.copy(alpha = 0.12f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun List<ReleaseScheduleItem>.groupByWeekday(
    month: YearMonth,
): Map<LocalDate, List<ReleaseScheduleItem>> {
    val result = linkedMapOf<LocalDate, MutableList<ReleaseScheduleItem>>()
    (1..month.lengthOfMonth()).forEach { day ->
        val date = month.atDay(day)
        val weekday = date.dayOfWeek
        filter { it.day.toDayOfWeek() == weekday }.forEach { item ->
            result.getOrPut(date) { mutableListOf() }.add(item)
        }
    }
    return result
}

private fun String.toDayOfWeek(): DayOfWeek? = when {
    contains("senin", true) -> DayOfWeek.MONDAY
    contains("selasa", true) -> DayOfWeek.TUESDAY
    contains("rabu", true) -> DayOfWeek.WEDNESDAY
    contains("kamis", true) -> DayOfWeek.THURSDAY
    contains("jumat", true) || contains("jumaat", true) -> DayOfWeek.FRIDAY
    contains("sabtu", true) -> DayOfWeek.SATURDAY
    contains("minggu", true) -> DayOfWeek.SUNDAY
    else -> null
}

private fun monthDates(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val total = leading + month.lengthOfMonth()
    val cells = (0 until total).map { index ->
        if (index < leading) null else month.atDay(index - leading + 1)
    }.toMutableList()
    while (cells.size % 7 != 0) cells += null
    return cells
}

private val WEEKDAY_LABELS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val MONTH_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))
private val DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))
