plugins {
    id("com.android.library")
}

android {
    namespace = "dev.samrat.wallet.paymentsdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    api("com.google.android.gms:play-services-wallet:20.0.0")
}
