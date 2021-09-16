import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Date
import java.text.SimpleDateFormat

val gitTagName: String? get() = Regex("(?<=refs/tags/).*").find(System.getenv("GITHUB_REF") ?: "")?.value
val gitCommitSha: String? get() = System.getenv("GITHUB_SHA") ?: null
val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").format(Date()) as String

val mainClassPath = "com.github.asforest.LittleClientMain"

group = "com.github.asforest"
version = gitTagName ?: "0.0.0"

plugins {
    kotlin("jvm") version "1.5.10"
    application
}

repositories {
//    maven { setUrl("http://maven.aliyun.com/nexus/content/groups/public/") }
    mavenCentral()
}

dependencies {
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")

    // implementation(fileTree("libs") {include("*.jar")})

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set(mainClassPath)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    // 添加Manifest
    manifest {
        attributes("Main-Class" to mainClassPath)
        attributes("Compile-Time" to timestamp)
        attributes("Application-Version" to archiveVersion.get())
        attributes("Author" to "Asforest")
        attributes("Git-Commit" to (gitCommitSha ?: ""))
        attributes("Compile-Time" to timestamp)
        attributes("Compile-Time-Ms" to System.currentTimeMillis())
    }

    // 复制依赖库
    from(configurations.runtimeClasspath.get().map {
        println("- "+it.name)
        if (it.isDirectory) it else zipTree(it)//.matching { exclude("*") }
    })

    // 打包assets目录里的文件
    from("assets")

    // 打包源代码
    sourceSets.main.get().allSource.sourceDirectories.map {
        if(it.name != "resources")
            from(it) {into("sources/"+it.name) }
    }

    // 设置输出路径
    destinationDirectory.set(File(project.buildDir, "production"))
}