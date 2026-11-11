pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gwAnd"
// app4webtrees/ — Git-Submodul. Aus dessen Modul shared/ nutzt gwAnd nur Bausteine (Theme-Farben, Avatar, Kalender,
//                 Datumseingabe, Karte, Fotobetrachter) und Darstellung (Baumlayout, Baumansicht, Zeitleiste).
//                 ViewModel, Bildschirme und Modelle hat gwAnd eigene.
// core/          — gwAnd-Kern: Client fuer die Gramps Web API, Modelle, Uebersetzer, ViewModel, Bildschirme.
// app/           — Android-Huelle.
include(":shared")
project(":shared").projectDir = file("app4webtrees/shared")
include(":core")
include(":app")
