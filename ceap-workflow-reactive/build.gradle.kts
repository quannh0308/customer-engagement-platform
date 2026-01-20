plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    // Internal dependencies
    implementation(project(":ceap-common"))
    implementation(project(":ceap-models"))
    implementation(project(":ceap-connectors"))
    implementation(project(":ceap-filters"))
    implementation(project(":ceap-scoring"))
    implementation(project(":ceap-storage"))

    // AWS Lambda
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.26"))
    implementation("software.amazon.awssdk:dynamodb")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}
