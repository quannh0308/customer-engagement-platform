dependencies {
    // Internal dependencies
    implementation(project(":solicitation-common"))
    implementation(project(":solicitation-models"))

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.26"))
    implementation("software.amazon.awssdk:sagemaker")
    implementation("software.amazon.awssdk:sagemakerruntime")
    implementation("software.amazon.awssdk:dynamodb")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    
    // Bean Validation
    implementation("javax.validation:validation-api:2.0.1.Final")
}
