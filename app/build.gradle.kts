import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("com.android.application")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.androidx.activity.ktx)
    implementation("com.google.zxing:core:3.5.3")
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"

task("downloadGeoFiles") {

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
    )

    fun openStreamFollowingRedirects(urlStr: String): java.io.InputStream {
        var location = urlStr
        repeat(10) {
            val conn = URL(location).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                location = conn.getHeaderField("Location") ?: throw Exception("Redirect with no Location header")
                conn.disconnect()
            } else {
                return conn.inputStream
            }
        }
        throw Exception("Too many redirects for $urlStr")
    }

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    openStreamFollowingRedirects(downloadUrl).use { input ->
                        Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    println("$outputFileName downloaded to $outputPath")
                    lastException = null
                    return@repeat
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < 2) {
                        println("Download failed (attempt ${attempt + 1}/3): ${e.message}, retrying...")
                        Thread.sleep(5_000L * (attempt + 1))
                    }
                }
            }
            lastException?.let { throw it }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
}