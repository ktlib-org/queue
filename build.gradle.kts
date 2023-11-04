buildscript {
    val kotlinVersion: String by project

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
    }
}

group = "org.ktlib"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

plugins {
    `java-library`
    `maven-publish`
}

apply(plugin = "kotlin")

dependencies {
    val kotlinVersion: String by project
    val kotestVersion: String by project
    val jacksonVersion: String by project

    implementation("com.github.ktlib-org:core:0.1.15")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("com.rabbitmq:amqp-client:5.14.2")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:1.13.4")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform { }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.ktlib"
            artifactId = "ktlib"
            version = "0.1.0"

            from(components["java"])

            pom {
                name.set("ktlib-queue")
                description.set("A library making some things easier in Kotlin")
                url.set("http://ktlib.org")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("aaronfreeman")
                        name.set("Aaron Freeman")
                        email.set("aaron@freeman.zone")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:ktlib-org/queue.git")
                    url.set("https://github.com/ktlib-org/queue")
                }
            }
        }
    }
}
