package app.samehadaku.dl.presentation.player

/** Android rejects Picture-in-Picture aspect ratios outside this range. */
internal const val MIN_PIP_ASPECT_RATIO = 0.42f
internal const val MAX_PIP_ASPECT_RATIO = 2.39f
private const val DEFAULT_PIP_ASPECT_RATIO = 16f / 9f

/**
 * Keeps a valid PiP ratio even when the decoded video reports a broken size.
 * Extracted so the boundary cases stay testable without an Activity.
 */
internal fun clampPipAspectRatio(ratio: Float): Float {
    if (!ratio.isFinite() || ratio <= 0f) return DEFAULT_PIP_ASPECT_RATIO
    return ratio.coerceIn(MIN_PIP_ASPECT_RATIO, MAX_PIP_ASPECT_RATIO)
}
