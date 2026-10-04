package org.finiteplay.core.session

/**
 * Elapsed play time as `m:ss`, counting minutes past sixty rather than rolling into hours —
 * a game that has run for seventy-five minutes reads `75:14`.
 *
 * Deliberately not locale-formatted. This is a stopwatch reading, where the digits mean the
 * same thing everywhere and a translator has nothing to decide; the surrounding sentence is
 * what gets localised, through a string resource that takes this as an argument.
 */
fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
