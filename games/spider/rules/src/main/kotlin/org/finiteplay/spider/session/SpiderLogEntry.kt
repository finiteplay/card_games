package org.finiteplay.spider.session

import org.finiteplay.spider.rules.Move

/**
 * One entry in the append-only committed-transaction log (`docs/PLATFORM.md` "Persistence").
 *
 * Two cases, against Klondike's four: Spider has no automatic-moves setting to toggle and no
 * automatic finish to record, so there is nothing for a third or fourth entry to describe
 * (`docs/games/spider/RULES.md` "What Spider does not have").
 */
sealed class SpiderLogEntry {
    data class PlayerMove(val move: Move) : SpiderLogEntry()
    data object Undo : SpiderLogEntry()
}
