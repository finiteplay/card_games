package org.finiteplay.klondike.tools.catalog

/**
 * Samples the graded population and reports what the level rules would actually admit, so a
 * threshold can be chosen from data and a quota checked before a rebuild spends hours arriving
 * at a level that cannot fill.
 *
 * Reports two things per grade, because the levels are cut on both:
 *
 * - **Forgiveness**: the share of complete games that win when every choice the tier calls
 *   equally good is made at random ([strategyPathWinRate]). High means a player who never backs
 *   up still gets there — the property Trivial and Easy are supposed to have.
 * - **Leakage**: whether the tier *below* can win it, by proof or by sampling. Anything above
 *   Trivial has to be free of it, and "the search ran out of budget" is not free of it.
 */
fun calibrateLevels(
    sampleSize: Int = 200,
    rulesetGradesPath: String = "tools/catalog/data/grading/ruleset_grades.csv",
    log: (String) -> Unit = ::println,
) {
    val grades = parseSeedGrades(gradingFile(rulesetGradesPath))
    check(grades.isNotEmpty()) { "no grades at $rulesetGradesPath" }

    log("sampling $sampleSize seeds per ruleset grade, ${FORGIVENESS_PATHS} games each")
    log("")
    log("grade    n    own >=90%   trivial rate: 0%   <2%   <5%  <20%  <50%  >=50%")

    for (ruleset in Ruleset.entries) {
        val pool = grades.filter { it.ruleset == ruleset }.take(sampleSize)
        if (pool.isEmpty()) continue

        var high = 0
        val trivialBuckets = IntArray(6)

        for (record in pool) {
            if (strategyPathWinRate(record.seed, ruleset) >= 0.9f) high++
            val trivial = if (ruleset == Ruleset.TRIVIAL) 1f else strategyPathWinRate(record.seed, Ruleset.TRIVIAL)
            trivialBuckets[
                when {
                    trivial == 0f -> 0
                    trivial < 0.02f -> 1
                    trivial < 0.05f -> 2
                    trivial < 0.20f -> 3
                    trivial < 0.50f -> 4
                    else -> 5
                }
            ]++
        }
        log(
            "%-8s %-4d %9d %14d %5d %5d %5d %5d %6d".format(
                ruleset.name, pool.size, high,
                trivialBuckets[0], trivialBuckets[1], trivialBuckets[2],
                trivialBuckets[3], trivialBuckets[4], trivialBuckets[5],
            ),
        )
    }
}
