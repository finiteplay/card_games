# S3b persisted formats (docs/games/spider/EXECUTION_PLAN.md).
#
# org.finiteplay.spider.session encodes the active-game log with frozen numeric opcodes
# (SpiderLogCodec) and reads/writes DataStore Preferences through plain string-literal keys.
# Neither depends on reflection or on a class/field name staying stable, so R8 renaming and
# shrinking cannot break that part of the format by itself.
#
# SuitCount is the one thing here that a build tool could break: the deal parameters persist it
# by *name* (SpiderActiveGameStore's SpiderDeal writes value.suitCount.name and reads it back by
# matching against SuitCount.entries), so anything that changed what .name returns would make
# every existing save unreadable in release builds only — the worst place to find out.
#
# This rule is precautionary, not a fix for an observed failure: the release build was tested
# both with and without it, and a save/restore round-trip through a minified APK succeeded either
# way, so R8 is not currently unboxing or renaming this enum. It is kept because nothing in the
# app declares that dependency otherwise, the cost is one unshrunk enum, and the failure it
# guards against would appear only in a shipped build. Klondike keeps the same rule for the same
# enums for the same reason.
-keepclassmembers enum org.finiteplay.spider.layout.SuitCount {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
