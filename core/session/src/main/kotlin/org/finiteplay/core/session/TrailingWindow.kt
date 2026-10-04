package org.finiteplay.core.session

/** Which trailing window a statistics screen aggregates over. */
enum class StatisticsPeriod { WEEK, MONTH, ALL_TIME }

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val WEEK_MILLIS = 7 * DAY_MILLIS
private const val MONTH_MILLIS = 30 * DAY_MILLIS

/**
 * Filters [records] to [period]'s trailing window ending at [nowMillis], reading each record's
 * timestamp through [timestampMillis].
 *
 * Week and Month are rolling 7-/30-day windows measured back from now, not calendar-boundary
 * weeks/months — simpler and timezone-independent, at the cost of not resetting on the 1st of
 * the month or a fixed weekday. Generic over the record type because the only thing this
 * function needs from one is a timestamp; a game's own richer record supplies it through
 * [timestampMillis] rather than this module knowing the record's shape.
 */
fun <T> filterByPeriod(records: List<T>, period: StatisticsPeriod, nowMillis: Long, timestampMillis: (T) -> Long): List<T> =
    when (period) {
        StatisticsPeriod.ALL_TIME -> records
        StatisticsPeriod.WEEK -> records.filter { timestampMillis(it) >= nowMillis - WEEK_MILLIS }
        StatisticsPeriod.MONTH -> records.filter { timestampMillis(it) >= nowMillis - MONTH_MILLIS }
    }
