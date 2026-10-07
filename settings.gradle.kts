pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Not "KlondikeSolitaire": this repo hosts a family of card games sharing one core
// (`docs/ARCHITECTURE.md`), of which Klondike is the first.
rootProject.name = "FinitePlayGames"

// Reusable by any card game — Blackjack as much as a solitaire.
include(":core:cards")
include(":core:session")
include(":core:storage")
include(":core:ui")

// Reusable by any *solitaire*. Blackjack would skip this layer entirely, which is the
// point of it being separate from :core.
include(":solitaire:catalog")
include(":solitaire:ui")

// One game, one app.
include(":games:klondike:rules")
include(":games:klondike:solver")
include(":games:klondike:app")

include(":games:spider:rules")
include(":games:spider:solver")
include(":games:spider:app")

include(":games:freecell:rules")
include(":games:freecell:solver")
include(":games:freecell:app")

include(":games:blackjack:rules")
include(":games:blackjack:app")

include(":games:holdem:rules")
include(":games:holdem:opponents")
include(":games:holdem:app")

// Desktop-only tooling; asserted never to reach an app's classpath.
include(":tools:catalog")
