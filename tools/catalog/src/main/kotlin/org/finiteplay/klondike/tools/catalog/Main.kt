package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.solver.StrategyTier
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits

/**
 * Desktop-only entry point for solving, certificate replay, catalog generation, and
 * integrity reports (D1s/D1b). `:app` must never depend on this module.
 *
 * Default: runs the D1s feasibility spike, certifying ten sample seeds and reporting
 * timing. D1b's real catalog generation (candidate ordinal ordering, binary/manifest
 * output, certificate commit) arrives later, after the rules freeze.
 *
 * `grade`: runs the node-count-based grading spike (superseded by `generate` below as
 * the interim catalog's actual grading source, kept as a documented prior approach)
 * over the 100 interim solvable seeds.
 *
 * `classify [n]`: calibration pass - classifies the first [n] (default: all) existing
 * interim seeds by strategy tier and reports the distribution and timing, without
 * generating anything.
 *
 * `purewin [n]`: the hierarchy check - plays each tier's rules unaided over [n] seeds
 * and reports win/stuck/move-cap counts per tier. Win rates must rise with the tier for
 * the cumulative-strategy premise (`docs/games/klondike/DIFFICULTY_LEVELS.md`) to hold at all; run this
 * after any change to the tier rulesets.
 *
 * `replay <seed> <catalogV> <rulesV> <shuffleV> <drawMode> <autoMoves> <base64Log>`:
 * renders a save pulled off a device back into a readable board plus the move list that
 * produced it, by replaying through the real reducer (`SaveInspection.kt`).
 *
 * `diagnose [positions]`: measures the *boards* rather than the rulesets, over the first
 * ten shipped seeds of Hard, Expert and Insane — foundation withdrawals, critical
 * choices, and the share of legal branches that lose. Answers why the top tiers rank
 * further apart than they feel; see `DifficultyDiagnostics.kt`.
 *
 * `generate [target]`: grows the interim solvable catalog from 100 to [target]
 * (default [TARGET_CATALOG_SIZE]) seeds using `:solver`'s strategy-tier classifier
 * (`docs/games/klondike/DIFFICULTY_LEVELS.md`), preserving the existing 100, and prints both seed
 * list and grade map as ready-to-paste Kotlin for `:app`. The optional target arg
 * exists mainly for a quick smoke run at a small size before the real 1,000-seed pass.
 *
 * `prepare-astar-tuning`, `eval-astar-ordering`, and `export-astar-ranking` form the
 * reproducible offline ordering-fit pipeline. The first freezes labels and data splits
 * from `solver_benchmark.csv`; the second evaluates independent coefficients against
 * the real solver; the third emits grouped legal-successor features for GPU ranking.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "grade" -> runGradingSpike(INTERIM_SEEDS_FOR_GRADING)
        "classify" -> reportClassification(INTERIM_SEEDS_FOR_GRADING.take(args.getOrNull(1)?.toIntOrNull() ?: INTERIM_SEEDS_FOR_GRADING.size))
        "purewin" -> reportPureRulesetWinRates(INTERIM_SEEDS_FOR_GRADING.take(args.getOrNull(1)?.toIntOrNull() ?: INTERIM_SEEDS_FOR_GRADING.size))
        "replay" -> reportSave(
            seed = args[1].toLong(),
            versions = GameVersions(args[2].toInt(), args[3].toInt(), args[4].toInt()),
            drawMode = DrawMode.valueOf(args[5]),
            initialAutomaticMovesEnabled = args[6].toBooleanStrict(),
            encodedLog = args[7],
            solve = args.getOrNull(8) == "solve",
        )
        "fault" -> reportFaultLocation(
            seed = args[1].toLong(),
            versions = GameVersions(args[2].toInt(), args[3].toInt(), args[4].toInt()),
            drawMode = DrawMode.valueOf(args[5]),
            initialAutomaticMovesEnabled = args[6].toBooleanStrict(),
            encodedLog = args[7],
        )
        "obvious" -> reportObviousTraps(
            seeds = args.drop(1).mapNotNull { it.toLongOrNull() }
                .ifEmpty { FIRST_SEEDS_BY_DIFFICULTY.getValue(AppDifficulty.TRIVIAL) },
            tier = StrategyTier.TRIVIAL,
        )
        "trivial" -> findTrivialSeeds(
            wanted = args.getOrNull(1)?.toIntOrNull() ?: 1,
            firstSeed = args.getOrNull(2)?.toLongOrNull() ?: 1L,
        )
        "walk" -> walkObviousGraph(
            seed = args.getOrNull(1)?.toLongOrNull() ?: 835L,
            maxDepth = args.getOrNull(2)?.toIntOrNull() ?: 1000,
        )
        "bench-trivial" -> benchmarkTrivial(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 200,
            maxBudget = args.getOrNull(2)?.toIntOrNull() ?: 3,
        )
        "calibrate-trivial" -> calibrateTrivial(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 2000,
            maxBudget = args.getOrNull(2)?.toIntOrNull() ?: 3,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 4,
            keepFrom = args.getOrNull(4)?.toIntOrNull() ?: 2,
            outputPath = args.getOrNull(5),
        )
        "solutions-trivial" -> {
            val csv = args.getOrNull(1) ?: ".claude/scratch/trivial_seeds.csv"
            val minBudget = args.getOrNull(2)?.toIntOrNull() ?: 3
            val seeds = java.io.File(csv).readLines().drop(1).mapNotNull { line ->
                val parts = line.split(',')
                val seed = parts.getOrNull(0)?.trim()?.toLongOrNull()
                val budget = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (seed != null && budget != null && budget >= minBudget) seed else null
            }
            // Which seeds to *drop* is read from a file rather than a compiled constant: the
            // shipped tier is now thousands of seeds, too many for one static initializer, and
            // it changes every time the tier is regenerated.
            val supersededPath = args.getOrNull(4)
            val superseded = if (supersededPath != null) {
                java.io.File(supersededPath).readLines().mapNotNull { it.trim().toLongOrNull() }
            } else {
                PREVIOUS_TRIVIAL_SEEDS
            }
            writeTrivialSolutions(
                trivialSeeds = seeds,
                previousTrivialSeeds = superseded,
                assetPath = args.getOrNull(3) ?: "games/klondike/app/src/main/assets/solutions.bin",
            )
        }
        // Easy grades on the same robustness criterion, under Easy's priority order, and
        // discards anything Trivial's own order already wins — otherwise the two tiers would
        // overlap on exactly the deals that make Easy pointless.
        "calibrate-easy" -> calibrateTrivial(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 2000,
            maxBudget = args.getOrNull(2)?.toIntOrNull() ?: 3,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 4,
            keepFrom = args.getOrNull(4)?.toIntOrNull() ?: 2,
            outputPath = args.getOrNull(5),
            ruleset = Ruleset.EASY,
            excludeWonBy = Ruleset.EASY.tiersBelow,
        )
        "calibrate-medium" -> calibrateTrivial(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 2000,
            maxBudget = args.getOrNull(2)?.toIntOrNull() ?: 3,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 4,
            keepFrom = args.getOrNull(4)?.toIntOrNull() ?: 1,
            outputPath = args.getOrNull(5),
            ruleset = Ruleset.MEDIUM,
            excludeWonBy = Ruleset.MEDIUM.tiersBelow,
        )
        // No `calibrate-hard`: the tier it graded — the setup move on its own — merged into
        // Medium when the ladder went to four (`Ruleset`). `calibrate-medium` grades both
        // additions together, which is now what Medium means.
        "calibrate-hard" -> error(
            "calibrate-hard is gone: the setup-move tier merged into Medium. Use calibrate-medium.",
        )
        // Asks the real solver, over the *full* legal move set with withdrawal switched off,
        // whether a deal can be won without ever un-banking a card. The catalog's own restricted
        // DFS cannot answer this: it searches tap-resolved obvious and setup moves only, and
        // "exhausting a restricted space proves nothing about the full game" (`Solver.kt`).
        "withdrawal-needed" -> {
            val budget = SolverLimits(
                maxNodes = args.getOrNull(1)?.toLongOrNull() ?: 3_000_000L,
                maxDurationMs = args.getOrNull(2)?.toLongOrNull() ?: 60_000L,
            )
            for (seed in args.drop(3).mapNotNull { it.toLongOrNull() }) {
                val started = System.nanoTime()
                val outcome = Solver.solve(
                    org.finiteplay.klondike.board.dealGame(seed, D1S_SPIKE_VERSIONS),
                    budget,
                    includeFoundationWithdrawal = false,
                )
                val ms = (System.nanoTime() - started) / 1_000_000
                val verdict = when (outcome) {
                    is SolveOutcome.Solved -> "WINNABLE WITHOUT WITHDRAWAL (${outcome.certificate.size} moves)"
                    is SolveOutcome.Unsolved -> "needs a withdrawal (space exhausted)"
                    else -> "no verdict (${outcome::class.simpleName})"
                }
                println("seed=$seed $verdict ${ms}ms")
            }
        }
        "census" -> censusByDepth(
            seed = args[1].toLong(),
            maxDepth = args.getOrNull(2)?.toIntOrNull() ?: 200,
            maxFrontier = args.getOrNull(3)?.toIntOrNull() ?: 400_000,
            includeWithdrawal = args.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
            partialOrder = args.getOrNull(5)?.toBooleanStrictOrNull() ?: false,
            canonicaliseEmpty = args.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
            forceSafe = args.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
            countPaths = args.getOrNull(8)?.toBooleanStrictOrNull() ?: true,
        )
        "line-width" -> reportLineWidth(seed = args[1].toLong())
        "bench-solvers" -> benchmarkSolvers(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 10_000,
            parallelism = args.getOrNull(2)?.toIntOrNull() ?: 12,
            dfsNodes = args.getOrNull(3)?.toLongOrNull() ?: 300_000L,
            astarNodes = args.getOrNull(4)?.toLongOrNull() ?: 300_000L,
            astarMs = args.getOrNull(5)?.toLongOrNull() ?: 8_000L,
            outputPath = args.getOrNull(6) ?: "tools/catalog/data/solver_benchmark.csv",
            progressPath = args.getOrNull(7) ?: "tools/catalog/data/solver_benchmark_progress.txt",
        )
        "prepare-astar-tuning" -> prepareAStarTuningSeeds(
            benchmarkPath = args.getOrNull(1) ?: "tools/catalog/data/solver_benchmark.csv",
            outputPath = args.getOrNull(2) ?: "tools/catalog/data/astar_tuning/seeds.csv",
        )
        "eval-astar-ordering" -> {
            val score = evaluateAStarOrdering(
                seedManifestPath = args[1],
                split = TuningSplit.valueOf(args[2].uppercase()),
                maxSeeds = args[3].toInt(),
                maxNodes = args[4].toLong(),
                maxDurationMs = args[5].toLong(),
                ordering = org.finiteplay.klondike.solver.search.SearchOrdering(
                    lowerBoundWeight = args[6].toInt(),
                    downCardWeight = args[7].toInt(),
                    aceBurialWeight = args[8].toInt(),
                    neededCardDepthWeight = args[9].toInt(),
                ),
                includeFoundationWithdrawal = args[10].toBooleanStrict(),
                trialOutputPath = args.getOrNull(11),
            )
            println("TUNING_RESULT ${score.toJson()}")
        }
        "export-astar-ranking" -> exportAStarRankingData(
            seedManifestPath = args[1],
            split = TuningSplit.valueOf(args[2].uppercase()),
            maxSeeds = args[3].toInt(),
            maxStatesPerSeed = args[4].toInt(),
            maxNodes = args[5].toLong(),
            maxDurationMs = args[6].toLong(),
            ordering = org.finiteplay.klondike.solver.search.SearchOrdering(
                lowerBoundWeight = args[7].toInt(),
                downCardWeight = args[8].toInt(),
                aceBurialWeight = args[9].toInt(),
                neededCardDepthWeight = args[10].toInt(),
            ),
            includeFoundationWithdrawal = args[11].toBooleanStrict(),
            outputPath = args[12],
        )
        // The reusable grading pass: stage A grades every seed by the lowest ruleset that wins
        // it, stage B searches only the deals no ruleset wins, and the catalog build reads the
        // file both write (`SeedGrading.kt`).
        "explain" -> explainSeeds(args.drop(1).mapNotNull { it.toLongOrNull() })
        "grade-seeds" -> gradeRulesetStage(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 10_000_000,
            firstSeed = args.getOrNull(2)?.toLongOrNull() ?: 1L,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 12,
            poolPerLevel = args.getOrNull(4)?.toIntOrNull() ?: 100_000,
            outputPath = args.getOrNull(5) ?: "tools/catalog/data/grading/ruleset_grades.csv",
            censusPath = args.getOrNull(6) ?: "tools/catalog/data/grading/ruleset_census.txt",
        )
        "grade-search" -> gradeSearchStage(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 10_000_000,
            firstSeed = args.getOrNull(2)?.toLongOrNull() ?: 1L,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 12,
            expertTarget = args.getOrNull(4)?.toIntOrNull() ?: 15_000,
            insaneTarget = args.getOrNull(5)?.toIntOrNull() ?: 15_000,
            outputPath = args.getOrNull(6) ?: "tools/catalog/data/grading/search_grades.csv",
            censusPath = args.getOrNull(7) ?: "tools/catalog/data/grading/search_census.txt",
        )
        "enrich-hard" -> enrichHardLevel(
            gradesPath = args.getOrNull(1) ?: "tools/catalog/data/grading/ruleset_grades.csv",
            limit = args.getOrNull(2)?.toIntOrNull() ?: 30_000,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 12,
        )
        "calibrate" -> calibrateLevels(
            sampleSize = args.getOrNull(1)?.toIntOrNull() ?: 200,
            rulesetGradesPath = args.getOrNull(2) ?: "tools/catalog/data/grading/ruleset_grades.csv",
        )
        "reorder-levels" -> reorderLevelCatalogs(
            dealDir = args.getOrNull(1) ?: "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
        )
        "build-levels" -> buildLevelCatalogs(
            rulesetGradesPath = args.getOrNull(1) ?: "tools/catalog/data/grading/ruleset_grades.csv",
            searchGradesPath = args.getOrNull(2) ?: "tools/catalog/data/grading/search_grades.csv",
            quota = args.getOrNull(3)?.toIntOrNull() ?: 10_000,
        )
        "build-catalogs" -> buildCertifiedCatalogs(
            dealDir = args.getOrNull(1) ?: DEFAULT_DEAL_DIR,
            solutionsPath = args.getOrNull(2) ?: DEFAULT_SOLUTIONS_PATH,
            outDir = args.getOrNull(3) ?: DEFAULT_CATALOG_DIR,
            catalogVersion = args.getOrNull(4)?.toIntOrNull() ?: 1,
        )
        "verify-catalogs" -> {
            val passed = verifyCertifiedCatalogs(
                catalogDir = args.getOrNull(1) ?: DEFAULT_CATALOG_DIR,
                solutionsPath = args.getOrNull(2) ?: DEFAULT_SOLUTIONS_PATH,
                sampleSize = args.getOrNull(3)?.toIntOrNull() ?: 25,
            )
            if (!passed) kotlin.system.exitProcess(1)
        }
        "classify-deals" -> classifyDeals(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 1000,
            maxNodes = args.getOrNull(2)?.toLongOrNull() ?: 1_000_000L,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 8,
        )
        "line-sweep" -> sweepRulesetSpread(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 20000,
            parallelism = args.getOrNull(2)?.toIntOrNull() ?: 8,
        )
        "spread-sweep" -> sweepFoundationSpread(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 500,
            maxNodes = args.getOrNull(2)?.toLongOrNull() ?: 200_000L,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 8,
            withProductiveSetup = args.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
        )
        "prune-eval" -> evaluatePruningRules(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 500,
            maxNodes = args.getOrNull(2)?.toLongOrNull() ?: 200_000L,
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 8,
        )
        "expert-one" -> {
            val cap = args.getOrNull(1)?.toIntOrNull() ?: 5_000_000
            for (seed in args.drop(2).mapNotNull { it.toLongOrNull() }) {
                val started = System.nanoTime()
                val v = checkWithdrawalTier(seed, cap)
                println("seed=$seed ${v.reason} points=${v.criticalPoints} choices=${v.criticalChoices} ${(System.nanoTime() - started) / 1_000_000}ms — ${v.detail}")
            }
        }
        "survey-expert" -> surveyWithdrawalCandidates(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 50000,
            parallelism = args.getOrNull(2)?.toIntOrNull() ?: 12,
            maxStates = args.getOrNull(3)?.toIntOrNull() ?: 2_000_000,
            outputPath = args.getOrNull(4),
            progressPath = args.getOrNull(5),
        )
        "calibrate-expert" -> calibrateWithdrawalTier(
            seedCount = args.getOrNull(1)?.toIntOrNull() ?: 2000,
            parallelism = args.getOrNull(2)?.toIntOrNull() ?: 4,
            maxStates = args.getOrNull(3)?.toIntOrNull() ?: 300_000,
            outputPath = args.getOrNull(4),
        )
        // Builds the withdrawal tier, which is now called Hard and ships as the Hard level. It
        // was `build-expert-tier` while the ladder had five rungs and this one was Expert.
        "build-hard-tier" -> buildTrivialTier(
            csvPath = args.getOrNull(1) ?: "tools/catalog/data/expert_survey.csv",
            minBudget = args.getOrNull(2)?.toIntOrNull() ?: MIN_CRITICAL,
            dealDir = args.getOrNull(3) ?: "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
            assetPath = args.getOrNull(4) ?: "games/klondike/app/src/main/assets/solutions.bin",
            supersededPath = args.getOrNull(5),
            tier = "Hard",
            ruleset = Ruleset.HARD,
            mustNotBeWonBy = Ruleset.HARD.tiersBelow,
            maxBudget = MAX_CRITICAL,
            ascending = true,
            bandLabel = "critical points",
        )
        "build-expert-tier" -> error(
            "build-expert-tier is gone: the withdrawal tier is now Hard. Use build-hard-tier.",
        )
        "build-medium-tier" -> buildTrivialTier(
            csvPath = args.getOrNull(1) ?: "tools/catalog/data/medium_graded.csv",
            minBudget = args.getOrNull(2)?.toIntOrNull() ?: 1,
            dealDir = args.getOrNull(3) ?: "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
            assetPath = args.getOrNull(4) ?: "games/klondike/app/src/main/assets/solutions.bin",
            supersededPath = args.getOrNull(5),
            tier = "Medium",
            ruleset = Ruleset.MEDIUM,
            mustNotBeWonBy = Ruleset.MEDIUM.tiersBelow,
        )
        "build-easy-tier" -> buildTrivialTier(
            csvPath = args.getOrNull(1) ?: "tools/catalog/data/easy_graded.csv",
            minBudget = args.getOrNull(2)?.toIntOrNull() ?: 1,
            dealDir = args.getOrNull(3) ?: "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
            assetPath = args.getOrNull(4) ?: "games/klondike/app/src/main/assets/solutions.bin",
            supersededPath = args.getOrNull(5),
            tier = "Easy",
            ruleset = Ruleset.EASY,
            mustNotBeWonBy = Ruleset.EASY.tiersBelow,
        )
        "build-trivial-tier" -> buildTrivialTier(
            csvPath = args.getOrNull(1) ?: "tools/catalog/data/trivial_tier6.csv",
            minBudget = args.getOrNull(2)?.toIntOrNull() ?: 6,
            dealDir = args.getOrNull(3) ?: "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
            assetPath = args.getOrNull(4) ?: "games/klondike/app/src/main/assets/solutions.bin",
            supersededPath = args.getOrNull(5),
        )
        "export-trivial" -> exportTrivialCatalog(
            csvPath = args.getOrNull(1) ?: ".claude/scratch/trivial_seeds.csv",
            outputDir = args.getOrNull(2) ?: "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
            minBudget = args.getOrNull(3)?.toIntOrNull() ?: 3,
        )
        "diagnose-play" -> diagnosePlay(
            seed = args[1].toLong(),
            versions = GameVersions(args[2].toInt(), args[3].toInt(), args[4].toInt()),
            drawMode = DrawMode.valueOf(args[5]),
            initialAutomaticMovesEnabled = args[6].toBooleanStrict(),
            encodedLog = args[7],
        )
        "verify-tier" -> verifyTier(
            csvPath = args.getOrNull(1) ?: "tools/catalog/data/trivial_seeds.csv",
            minBudget = args.getOrNull(2)?.toIntOrNull() ?: 3,
        )
        "scan-unconditional" -> scanUnconditional(
            csvPath = args.getOrNull(1) ?: "tools/catalog/data/trivial_tier6.csv",
            outputPath = args.getOrNull(2),
            parallelism = args.getOrNull(3)?.toIntOrNull() ?: 12,
        )
        "unconditional" -> reportUnconditional(
            args.drop(1).mapNotNull { it.toLongOrNull() }.ifEmpty { listOf(467151L) },
        )
        "limit" -> reportRobustnessLimit(
            seed = args.getOrNull(1)?.toLongOrNull() ?: 467151L,
            ceiling = args.getOrNull(2)?.toIntOrNull() ?: 120,
        )
        "audit-trivial" -> auditTrivialSeeds(
            args.drop(1).mapNotNull { it.toLongOrNull() }
                .ifEmpty { FIRST_SEEDS_BY_DIFFICULTY.getValue(AppDifficulty.TRIVIAL) },
        )
        "diagnose" -> reportDifficultyDiagnostics(
            probePositions = args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PROBE_POSITIONS,
        )
        "generate" -> {
            val target = args.getOrNull(1)?.toIntOrNull() ?: TARGET_CATALOG_SIZE
            printKotlinExport(generateSeeds(INTERIM_SEEDS_FOR_GRADING, target = target))
        }
        "generate-tiers" -> {
            val perTier = args.getOrNull(1)?.toIntOrNull() ?: TARGET_CATALOG_SIZE
            val byDifficulty = generatePerDifficulty(seedsPerTier = perTier)
            printPerDifficultyExport(byDifficulty)
            args.getOrNull(2)?.let { writeSolutionCatalog(byDifficulty, java.io.File(it)) }
        }
        else -> runD1sSpike(limits = SolverLimits())
    }
}
