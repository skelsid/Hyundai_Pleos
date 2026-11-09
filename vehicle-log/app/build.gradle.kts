plugins {
    id("com.android.application")
}

android {
    namespace = "dev.vehiclelog"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vehiclelog"
        // Pleos 차량 보안 요건: minSdk 28 이상 (setup-connect-sdk-emulator 문서)
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    testImplementation("junit:junit:4.13.2")
}
