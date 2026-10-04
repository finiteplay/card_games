# S1 persisted formats (docs/games/klondike/EXECUTION_PLAN.md).
#
# org.finiteplay.klondike.storage encodes the active-game log and history records with
# manual DataOutputStream/DataInputStream I/O keyed by MoveOpcode's frozen numeric IDs,
# and reads/writes DataStore Preferences through plain string-literal keys. Neither
# depends on reflection or on a class/field name staying stable, so R8 renaming and
# shrinking cannot break the on-disk format by itself. The one thing that format does
# depend on is enum ordinal order, which R8 does not change, but keep these enums'
# standard members reachable regardless of future obfuscation passes so ordinal-based
# decoding (LogEntryCodec, HistoryStore) always has a real enum to resolve against.
-keepclassmembers enum org.finiteplay.klondike.game.deal.MoveOpcode {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum org.finiteplay.klondike.game.card.Suit {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum org.finiteplay.klondike.storage.Outcome {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
