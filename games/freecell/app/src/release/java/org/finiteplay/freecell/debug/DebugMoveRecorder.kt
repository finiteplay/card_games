package org.finiteplay.freecell.debug

import org.finiteplay.freecell.ui.game.MoveRecorder
import java.io.File

/** Release counterpart of the debug move recorder: records nothing. */
fun debugMoveRecorder(filesDir: File): MoveRecorder? = null
