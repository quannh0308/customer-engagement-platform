plugins {
    kotlin("jvm") version "1.9.21" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = "com.solicitation"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }

    dependencies {
        // Kotlin standard library
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")

        // Logging (all modules)
        "implementation"("org.slf4j:slf4j-api:2.0.9")
        "implementation"("ch.qos.logback:logback-classic:1.4.14")
        "implementation"("ch.qos.logback:logback-core:1.4.14")
        "implementation"("io.github.microutils:kotlin-logging-jvm:3.0.5")

        // Testing (all modules)
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.1")
        "testImplementation"("net.jqwik:jqwik:1.8.2")
        "testImplementation"("io.mockk:mockk:1.13.8")
        "testImplementation"("org.assertj:assertj-core:3.24.2")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
