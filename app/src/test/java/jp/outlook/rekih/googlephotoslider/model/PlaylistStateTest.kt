package jp.outlook.rekih.googlephotoslider.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class PlaylistStateTest {
    private val utc = ZoneId.of("UTC")

    private fun item(id: String, date: String) =
        MediaItem(id = id, productUrl = "", mediaMetadata = MediaMetadata("${date}T10:00:00.123Z"))

    // A: 0,1  B: 2,3,4  C: 5
    private val items = listOf(
        item("a1", "2021-01-01"), item("a2", "2021-01-01"),
        item("b1", "2021-01-02"), item("b2", "2021-01-02"), item("b3", "2021-01-02"),
        item("c1", "2021-01-03"),
    )

    private fun state(complete: Boolean, size: Int = items.size) =
        PlaylistState(items.take(size), complete, utc)

    @Test
    fun next() {
        assertEquals(1, state(true).move(0, Nav.NEXT))
        assertEquals(0, state(true).move(5, Nav.NEXT))
        // 全件読み込み前は末尾で折り返さず、読み込みを待つ
        assertNull(state(false).move(5, Nav.NEXT))
    }

    @Test
    fun prev() {
        assertEquals(1, state(true).move(2, Nav.PREV))
        assertEquals(5, state(true).move(0, Nav.PREV))
        assertEquals(0, state(false).move(0, Nav.PREV))
    }

    @Test
    fun nextDate() {
        assertEquals(2, state(true).move(0, Nav.NEXT_DATE))
        assertEquals(5, state(true).move(3, Nav.NEXT_DATE))
        assertEquals(0, state(true).move(5, Nav.NEXT_DATE))
        // 読み込み済みの範囲に次の日付がなければ決まらない
        assertNull(state(false, size = 4).move(2, Nav.NEXT_DATE))
    }

    @Test
    fun prevDate() {
        assertEquals(2, state(true).move(5, Nav.PREV_DATE))
        assertEquals(0, state(true).move(3, Nav.PREV_DATE))
        assertEquals(0, state(true).move(2, Nav.PREV_DATE))
        assertEquals(0, state(true).move(1, Nav.PREV_DATE))
    }

    @Test
    fun empty() {
        assertNull(PlaylistState(complete = false).move(0, Nav.NEXT))
        assertEquals(0, PlaylistState(complete = true).move(0, Nav.PREV_DATE))
    }

    @Test
    fun unparseableDateIsOneGroup() {
        val s = PlaylistState(listOf(item("x", "bad"), item("y", "bad"), item("z", "2021-01-01")), true, utc)
        assertEquals(2, s.move(0, Nav.NEXT_DATE))
    }
}
