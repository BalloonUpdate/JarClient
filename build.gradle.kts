import java.util.Date
import java.text.SimpleDateFormat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun getVersionName(tagName: String) = if(tagName.startsWith("v")) tagName.substring(1) else tagName
val gitTagName: String? get() = Regex("(?<=refs/tags/).*").find(System.getenv("GITHUB_REF") ?: "")?.value
val gitCommitSha: String? get() = System.getenv("GITHUB_SHA") ?: null
val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").format(Date()) as String
val debugVersion: String get() = System.getenv("DBG_VERSION") ?: "0.0.0"

group = "com.github.balloonupdate"
version = gitTagName?.run { getVersionName(this) } ?: debugVersion

plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:1.30")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.json:json:20220320")
    implementation("com.hrakaroo:glob:0.9.0")
    implementation("com.formdev:flatlaf:2.4")
    implementation("com.formdev:flatlaf-intellij-themes:2.4")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("com.github.balloonupdate.Main")
}

tasks.withType<ShadowJar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    manifest {
        attributes("Version" to archiveVersion.get())
        attributes("Git-Commit" to (gitCommitSha ?: ""))
        attributes("Compile-Time" to timestamp)
        attributes("Compile-Time-Ms" to System.currentTimeMillis())
        attributes("Premain-Class" to "com.github.balloonupdate.Main")
    }

    destinationDirectory.set(File(project.buildDir, "production"))
    archiveClassifier.set("")
}
