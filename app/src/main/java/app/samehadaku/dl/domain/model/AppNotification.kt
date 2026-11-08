package app.samehadaku.dl.domain.model

/** What a notification is about; drives the icon and accent in the notification center. */
enum class AppNotificationKind {
    NEW_EPISODES,
    QUEUED,
    COMPLETED,
    VERIFICATION,
    INFO,
}

/**
 * A notification kept in the in-app inbox. [route] is the screen the notification
 * belongs to, so tapping it always lands on the related menu.
 */
data class AppNotification(
    val id: String,
    val kind: AppNotificationKind,
    val title: String,
    val message: String,
    val route: String,
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false,
)
