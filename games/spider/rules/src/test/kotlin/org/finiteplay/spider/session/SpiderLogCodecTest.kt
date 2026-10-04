package org.finiteplay.spider.session

import org.finiteplay.spider.rules.Move
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpiderLogCodecTest {
    @Test
    fun `an empty log round-trips`() {
        assertEquals(emptyList<SpiderLogEntry>(), decodeSpiderLog(encodeSpiderLog(emptyList())))
    }

    @Test
    fun `every entry kind round-trips in order`() {
        val log = listOf(
            SpiderLogEntry.PlayerMove(Move.TableauToTableau(0, 5, 9)),
            SpiderLogEntry.PlayerMove(Move.DealRow),
            SpiderLogEntry.Undo,
            SpiderLogEntry.PlayerMove(Move.TableauToTableau(9, 0, 3)),
        )
        assertEquals(log, decodeSpiderLog(encodeSpiderLog(log)))
    }

    @Test
    fun `the extreme column and index values a board can reach survive a round trip`() {
        // Nine is the last column; a column can hold every card dealt to it, so the lift index
        // runs well past what a single byte's sign bit would allow if this were read as signed
        // beyond 127 — pinned here so a wider board or deeper column fails this rather than
        // silently wrapping into a different move.
        val log = listOf(SpiderLogEntry.PlayerMove(Move.TableauToTableau(9, 100, 0)))
        assertEquals(log, decodeSpiderLog(encodeSpiderLog(log)))
    }

    @Test
    fun `the opcode bytes are the ones on disk`() {
        // These three numbers are in every installed save. If this test is edited rather than
        // fixed, every existing save decodes into a different game.
        assertArrayEquals(
            byteArrayOf(1, 2, 4, 6, 2, 3),
            encodeSpiderLog(
                listOf(
                    SpiderLogEntry.PlayerMove(Move.TableauToTableau(2, 4, 6)),
                    SpiderLogEntry.PlayerMove(Move.DealRow),
                    SpiderLogEntry.Undo,
                ),
            ),
        )
    }

    @Test
    fun `an unknown opcode is rejected rather than skipped`() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeSpiderLog(byteArrayOf(99))
        }
    }

    @Test
    fun `a truncated tableau move is rejected rather than partially read`() {
        // Three bytes of a four-byte entry: everything before it decoded fine, which is exactly
        // when returning "what we got" would look like a successful restore of the wrong board.
        assertThrows(IllegalArgumentException::class.java) {
            decodeSpiderLog(byteArrayOf(SpiderLogCodec.OPCODE_DEAL_ROW, 1, 0, 5))
        }
    }
}
