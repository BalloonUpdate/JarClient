import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Date
import java.text.SimpleDateFormat

plugins {
    kotlin("jvm") version "1.5.10"
    application
}

val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date())
val mainClassPath = "com.github.asforest.LittleClientMain"
group = "com.github.asforest"
version = "0.0.3"

repositories {
//    maven { setUrl("http://maven.aliyun.com/nexus/content/groups/public/") }
    mavenCentral()
}

dependencies {
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")

//    val log4j = "2.14.1"
//    implementation("org.apache.logging.log4j:log4j-api:$log4j")
//    implementation("org.apache.logging.log4j:log4j-core:$log4j")

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
        attributes("Application-Version" to archiveVersion)
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