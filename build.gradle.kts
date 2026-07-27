plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

// Some current libraries request a newer Kotlin standard library transitively.
// The project compiler is intentionally Kotlin 2.2.21, so keep all stdlib
// variants on exactly the same metadata version.
allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (
                requested.group == "org.jetbrains.kotlin" &&
                (requested.name.startsWith("kotlin-stdlib") || requested.name == "kotlin-reflect")
            ) {
                useVersion("2.2.21")
                because("Kotlin compiler and runtime metadata must use the same 2.2.21 version")
            }
        }
    }
}
