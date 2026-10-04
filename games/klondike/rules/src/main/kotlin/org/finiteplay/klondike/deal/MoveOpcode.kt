package org.finiteplay.klondike.deal

/**
 * Frozen primitive move-log opcode IDs. This is the same encoding used for solution
 * certificates (D1s/D1b) and the active-game save format (S1); freeze the numeric ID
 * once here for both uses. E1/E2a/E2b implement the payload each opcode carries
 * (source/destination pile, count, flips, and parked-card bookkeeping) against these
 * IDs. Never renumber a shipped ID; append new opcodes instead.
 */
enum class MoveOpcode(val id: Byte) {
    DRAW(0),
    RECYCLE(1),
    FLIP(2),
    TABLEAU_TO_TABLEAU(3),
    TABLEAU_TO_FOUNDATION(4),
    WASTE_TO_TABLEAU(5),
    WASTE_TO_FOUNDATION(6),
    FOUNDATION_TO_TABLEAU(7),
    UNDO(8),
    AUTO_FINISH(9),
    ;

    companion object {
        private val byId = entries.associateBy(MoveOpcode::id)
        fun fromId(id: Byte): MoveOpcode =
            byId[id] ?: throw IllegalArgumentException("Unknown move opcode id: $id")
    }
}
