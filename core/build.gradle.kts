// gwAnd-Kern: Client fuer die Gramps Web API, Modelle, Uebersetzer in die geteilte Darstellung, ViewModel, Bildschirme.
// Ziele wie app4webtrees/shared: Android und Desktop-JVM. Die Desktop-JVM dient den Tests (ohne Geraet).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi", "-Xexpect-actual-classes")
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // Bausteine und Darstellung aus app4webtrees (Submodul); bringt Compose, OkHttp, Serialisierung und Coil mit.
            api(project(":shared"))
            // Eigene Texte (Res-Klasse de.bgghome.gwand.res); direkt nennen, sonst erzeugt das Plugin die Klasse nicht.
            implementation(compose.components.resources)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
    }
}

compose.resources {
    packageOfResClass = "de.bgghome.gwand.res"
    publicResClass = true
    generateResClass = always
}

android {
    namespace = "de.bgghome.gwand.core"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Tests lesen die Musterantworten aus testdaten/ (Falkenrath); der Live-Test laeuft nur mit Passwortdatei.
tasks.withType<Test>().configureEach {
    systemProperty("gwand.testdaten", rootProject.file("testdaten").absolutePath)
}
