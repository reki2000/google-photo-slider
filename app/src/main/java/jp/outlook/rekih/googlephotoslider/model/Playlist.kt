package jp.outlook.rekih.googlephotoslider.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

enum class Nav { NEXT, PREV, NEXT_DATE, PREV_DATE }

// アルバム内のアイテム一覧（読み込み済みの分）と、全件読み込み済みかどうか
data class PlaylistState(
    val items: List<MediaItem> = listOf(),
    val complete: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dates: List<LocalDate?> by lazy { items.map { it.localDate(zone) } }

    // from から nav で移動した先の index。
    // 未読み込みの範囲を見ないと決まらない場合は null（読み込みが進めば決まる）。
    // 末尾から先頭への折り返しは全件読み込み済みの場合のみ行う。
    fun move(from: Int, nav: Nav): Int? {
        if (items.isEmpty()) return if (complete) 0 else null
        return when (nav) {
            Nav.NEXT -> next(from)
            Nav.PREV -> prev(from)
            Nav.NEXT_DATE -> nextDate(from)
            Nav.PREV_DATE -> prevDate(from)
        }
    }

    private fun next(from: Int): Int? = when {
        from + 1 < items.size -> from + 1
        complete -> 0
        else -> null
    }

    // 先頭で戻る場合、全件読み込み前は末尾が未確定なので先頭に留まる
    private fun prev(from: Int): Int = when {
        from > 0 -> from - 1
        complete -> items.size - 1
        else -> 0
    }

    // 撮影日（ローカル日付）が変わる最初のアイテムへ
    private fun nextDate(from: Int): Int? {
        val date = dates[from]
        for (i in from + 1 until items.size) {
            if (dates[i] != date) return i
        }
        return if (complete) 0 else null
    }

    // 1つ前の日付のグループの先頭へ
    private fun prevDate(from: Int): Int {
        val start = groupStart(from)
        return if (start > 0) groupStart(start - 1) else 0
    }

    private fun groupStart(from: Int): Int {
        var i = from
        while (i > 0 && dates[i - 1] == dates[from]) i--
        return i
    }
}

fun MediaItem.localDate(zone: ZoneId): LocalDate? = try {
    Instant.parse(mediaMetadata.creationTime).atZone(zone).toLocalDate()
} catch (e: DateTimeParseException) {
    null
}
