plugins {
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("com.android.library")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":service"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.viewpager)
    implementation(libs.google.material)
}
