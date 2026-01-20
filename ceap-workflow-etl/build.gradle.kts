plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    // Internal dependencies
    implementation(project(":ceap-common"))
    implementation(project(":ceap-models"))
    implementation(project(":ceap-connectors"))
    implementation(project(":ceap-storage"))

    // AWS Lambda
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")
    
    // AWS SDK v2
    implementation("software.amazon.awssdk:cloudwatch:2.20.26")
    implementation("software.amazon.awssdk:eventbridge:2.20.26")
    
    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}
