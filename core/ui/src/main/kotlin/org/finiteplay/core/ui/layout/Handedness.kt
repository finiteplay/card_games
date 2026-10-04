package org.finiteplay.core.ui.layout

/**
 * Which side of the board the player's dominant hand reaches from, and so which edge the
 * in-play controls and the piles a player touches most are drawn at (`docs/PLATFORM.md`
 * "Accessibility").
 *
 * Purely a layout mirror. No index, no ordering, and no rule anywhere depends on it — only
 * where a group is drawn changes, so a game can honour it without its rules engine ever
 * knowing the setting exists.
 */
enum class Handedness { RIGHT, LEFT }
