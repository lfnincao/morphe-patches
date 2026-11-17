import com.android.build.api.dsl.ApplicationExtension

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:opera:stub"))
    compileOnly(libs.morphe.extensions.library)
}

configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}