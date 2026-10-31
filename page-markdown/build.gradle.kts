plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
            implementation("io.ktor:ktor-client-core:3.3.3")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.3.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.3.3")
        }
    }
}

android {
    namespace = "com.yunfie.pagemarkdown"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
}
