import java.io.InputStreamReader
import java.util.Properties

plugins {
    id("com.android.application")
}

// 릴리스 서명 정보는 저장소 밖(커밋 금지) keystore.properties 에서 읽는다. 없으면 서명 없는 릴리스가 나온다
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

// 운행기록표를 보내는 Gmail 발신 계정도 저장소 밖(커밋 금지) mail.properties 에서 읽는다. 없으면 메일 보내기가 "발신 계정 미설정" 으로 실패한다
val mailFile = rootProject.file("mail.properties")
val mail = Properties().apply { if (mailFile.exists()) mailFile.inputStream().use { load(InputStreamReader(it, Charsets.UTF_8)) } }
fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.vehiclelog"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vehiclelog"
        // Pleos 차량 보안 요건: minSdk 28 이상 (setup-connect-sdk-emulator 문서)
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "MAIL_USER", quoted(mail.getProperty("mailUser", "").trim()))
        // Gmail 앱 비밀번호는 4자리씩 띄어 보여 주므로 공백을 지운다
        buildConfigField("String", "MAIL_APP_PASSWORD", quoted(mail.getProperty("mailAppPassword", "").replace(" ", "")))
        buildConfigField("String", "MAIL_FROM_NAME", quoted(mail.getProperty("mailFromName", "Hyundai Vehicle Log").trim()))
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Pleos SDK 가 리플렉션을 쓰는지 확인하지 못해 난독화·축소는 끈다
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
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
