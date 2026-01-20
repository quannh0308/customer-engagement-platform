dependencies {
    // Internal dependencies
    implementation(project(":ceap-common"))
    implementation(project(":ceap-models"))
    implementation(project(":ceap-storage"))
    
    // AWS Lambda
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.1")
    
    // AWS SDK v2
    implementation("software.amazon.awssdk:dynamodb:2.20.26")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Validation
    implementation("javax.validation:validation-api:2.0.1.Final")
    
    // Test dependencies
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-core:5.7.0")
}
