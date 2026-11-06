package app.samehadaku.dl.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand accents cycled deterministically per title, so an anime keeps the same
 * colour on every card (Koleksi folders and Unduhan task cards alike).
 */
private val TitleAccents = listOf(BrandPrimary, BrandAccent, BrandMint, BrandGold, BrandPink)

fun titleAccent(key: String): Color {
    val seed = key.ifBlank { "SamehadakuDL" }.hashCode() and Int.MAX_VALUE
    return TitleAccents[seed % TitleAccents.size]
}
