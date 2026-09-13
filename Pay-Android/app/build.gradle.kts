plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val googlePayEnvironment = providers.gradleProperty("GOOGLE_PAY_ENVIRONMENT").orElse("TEST")
val googlePayMerchantId = providers.gradleProperty("GOOGLE_PAY_MERCHANT_ID").orElse("")
val googlePayMerchantName = providers.gradleProperty("GOOGLE_PAY_MERCHANT_NAME").orElse("Pay Android")
val googlePayGateway = providers.gradleProperty("GOOGLE_PAY_GATEWAY").orElse("example")
val googlePayGatewayMerchantId = providers.gradleProperty("GOOGLE_PAY_GATEWAY_MERCHANT_ID").orElse("exampleGatewayMerchantId")
val googlePayCurrencyCode = providers.gradleProperty("GOOGLE_PAY_CURRENCY_CODE").orElse("USD")
val googlePayCountryCode = providers.gradleProperty("GOOGLE_PAY_COUNTRY_CODE").orElse("US")
val paymentBackendUrl = providers.gradleProperty("PAYMENT_BACKEND_URL").orElse("")

android {
    namespace = "dev.samrat.wallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.samrat.wallet"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "GOOGLE_PAY_ENVIRONMENT", googlePayEnvironment.get().asBuildConfigString())
        buildConfigField("String", "GOOGLE_PAY_MERCHANT_ID", googlePayMerchantId.get().asBuildConfigString())
        buildConfigField("String", "GOOGLE_PAY_MERCHANT_NAME", googlePayMerchantName.get().asBuildConfigString())
        buildConfigField("String", "GOOGLE_PAY_GATEWAY", googlePayGateway.get().asBuildConfigString())
        buildConfigField("String", "GOOGLE_PAY_GATEWAY_MERCHANT_ID", googlePayGatewayMerchantId.get().asBuildConfigString())
        buildConfigField("String", "GOOGLE_PAY_CURRENCY_CODE", googlePayCurrencyCode.get().asBuildConfigString())
        buildConfigField("String", "GOOGLE_PAY_COUNTRY_CODE", googlePayCountryCode.get().asBuildConfigString())
        buildConfigField("String", "PAYMENT_BACKEND_URL", paymentBackendUrl.get().asBuildConfigString())
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0-rc01")
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.12.0-rc01")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0-rc01")
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("zone.ien.hig:hig:1.3.1")
    implementation("zone.ien.hig:hig-icons-extended:1.3.1")
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation(project(":wallet-pay-sdk"))

    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.12.0-rc01")
}
