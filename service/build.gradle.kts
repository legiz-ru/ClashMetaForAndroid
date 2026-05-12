plugins {
    kotlin("android")
    id("kotlinx-serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

// ── Relay (whitelist-bypass) build ──────────────────────────────────────────
// librelay.so is a standalone Go binary (GOOS=linux, CGO_ENABLED=0) that runs
// as a subprocess providing SOCKS5 endpoint for VK/Telemost/WBStream tunnels.
// Sources live in the whitelist-bypass git submodule; we never copy them.

val relaySourceDir = rootProject.file("whitelist-bypass/relay")
val relayAbis = listOf(
    Triple("arm64-v8a",   "arm64", null),
    Triple("armeabi-v7a", "arm",   "7"),
)

relayAbis.forEach { (abi, goarch, goarm) ->
    val outFile = file("src/main/jniLibs/$abi/librelay.so")
    val taskName = "buildRelay_${abi.replace("-", "_")}"

    tasks.register<Exec>(taskName) {
        description = "Build librelay.so for $abi from whitelist-bypass submodule"
        group = "relay"

        workingDir = relaySourceDir
        environment("GOOS",         "linux")
        environment("GOARCH",       goarch)
        environment("CGO_ENABLED",  "0")
        if (goarm != null) environment("GOARM", goarm)
        commandLine("go", "build", "-trimpath", "-ldflags", "-s -w",
            "-o", outFile.absolutePath, ".")

        // Use @Optional so Gradle doesn't fail validation when the submodule
        // hasn't been initialised yet (inputs.dir requires the path to exist).
        inputs.files(fileTree(relaySourceDir) { include("**/*.go", "go.mod", "go.sum") })
            .withPropertyName("relaySources")
            .optional(true)
        outputs.file(outFile)
    }
}

tasks.named("preBuild") {
    relayAbis.forEach { (abi, _, _) ->
        dependsOn("buildRelay_${abi.replace("-", "_")}")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))

    ksp(libs.kaidl.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kaidl.runtime)
    implementation(libs.rikkax.multiprocess)
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))

    // define any required OkHttp artifacts without version
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
}

afterEvaluate {
    android {
        libraryVariants.forEach {
            sourceSets[it.name].kotlin.srcDir(buildDir.resolve("generated/ksp/${it.name}/kotlin"))
            sourceSets[it.name].java.srcDir(buildDir.resolve("generated/ksp/${it.name}/java"))
        }

        // JS assets from whitelist-bypass submodule — no copying needed,
        // Gradle merges multiple asset dirs automatically.
        sourceSets["main"].assets.srcDirs(
            "src/main/assets",
            rootProject.file("whitelist-bypass/android-app/app/src/main/assets")
        )
    }
}