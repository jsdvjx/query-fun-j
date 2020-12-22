plugins {
//    application
    kotlin("jvm") version "1.4.10"
    id("org.jetbrains.kotlin.plugin.noarg") version "1.4.10"
    java
    `maven-publish`
}

allprojects {
    repositories {
        maven { setUrl("https://maven.aliyun.com/repository/google/") }
        maven { setUrl("https://maven.aliyun.com/repository/jcenter/") }
        maven { setUrl("https://repo.spring.io/snapshot") }
        maven { setUrl("https://repo.spring.io/milestone") }
        //https://repo.spring.io/milestone
    }
}
//
//application {
//    mainClassName = "MainKt"
//}
println(project.properties.get("mavenUser"))

group = "st.wing"
version = "1.0-SNAPSHOT"

configure<org.jetbrains.kotlin.noarg.gradle.NoArgExtension> {
    invokeInitializers = true
    annotation("st.wing.query2fun.NoArg")
}
repositories {
    mavenCentral()
}

val kotestVersion = "4.3.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())

    implementation("io.projectreactor:reactor-core:3.2.3.RELEASE")
//    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion") // optional, for kotest assertions
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion") // requi
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.r2dbc:r2dbc-spi:0.8.3.RELEASE")
    implementation("io.r2dbc:r2dbc-postgresql:0.8.6.RELEASE")
    implementation("com.github.spullara.mustache.java:compiler:0.9.7")
    implementation("com.google.guava:guava:30.1-jre")
    implementation("org.javassist:javassist:3.27.0-GA")
    implementation(kotlin("compiler-embeddable"))
    testCompile("junit", "junit", "4.12")
}
//tasks.withType<Jar> {
//    manifest {
//        attributes["Main-Class"] = "MainKt"
//    }
//    from(sourceSets.main.get().output)
//    dependsOn(configurations.runtimeClasspath)
//    from({
//        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
//    })
//}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    val file = project.buildDir.resolve("tmp/Storage.kt")
    file.writeText(
        """
        package st.wing.query2fun
        class QueryMapperStorage{
            companion object {
             public   val SQL:MutableMap<String,String> = mutableMapOf("st.wing.query2fun.Mapper.get" to "select * from minute_history where id = $1")
            }
        }
    """.trimIndent()
    )
    source(file)
}
tasks.register<Copy>("distJar") {
    dependsOn("build")
    from(project.buildDir.resolve("libs"))
    into("C:\\Users\\JinXing\\project\\metal-policy\\libs")
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
            pom {
                name.set("My Library")
                description.set("A concise description of my library")
                url.set("http://www.example.com/library")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("Jin")
                        name.set("JinXing")
                        email.set("admin@at.ci")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            val type = (if (version.toString().endsWith("snapshot", ignoreCase = true)) "snapshots" else "releases")
            val urlStr = "http://188.40.83.243:8081/repository/maven-$type/"
            url = uri(urlStr)
            credentials {
                username = "admin"
                password = "29915815Jx"
            }
        }
    }
}
