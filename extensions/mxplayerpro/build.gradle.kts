import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly("androidx.appcompat:appcompat:1.7.0")
}

configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            proguardFiles("../proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles("../proguard-rules.pro")
        }
    }
}