package org.finiteplay.spider.debug

import org.finiteplay.spider.ui.game.MoveRecorder
import java.io.File

/** Release counterpart of the debug move recorder: records nothing. */
fun debugMoveRecorder(filesDir: File): MoveRecorder? = null
