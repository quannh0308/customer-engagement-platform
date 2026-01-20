plugins {
    id("com.github.johnrengelman.shadow")
    kotlin("plugin.serialization") version "1.9.21"
}

dependencies {
    // Internal dependencies
    implementation(project(":ceap-common"))

    // JSON Processing - Jackson with Kotlin support
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Kotlin Serialization (alternative to Jackson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Bean Validation
    implementation("javax.validation:validation-api:2.0.1.Final")
    implementation("org.hibernate.validator:hibernate-validator:7.0.5.Final")
    implementation("org.glassfish:jakarta.el:4.0.2") // Required for Hibernate Validator
    
    // Bean Validation for tests
    testImplementation("javax.validation:validation-api:2.0.1.Final")
    testImplementation("org.hibernate.validator:hibernate-validator:7.0.5.Final")
    testImplementation("org.glassfish:jakarta.el:4.0.2")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}
