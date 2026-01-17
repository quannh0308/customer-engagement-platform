dependencies {
    // Internal dependencies
    implementation(project(":solicitation-common"))
    implementation(project(":solicitation-models"))

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.26"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:kinesis")
    implementation("software.amazon.awssdk:athena")
    
    // JSON Schema validation
    implementation("com.networknt:json-schema-validator:1.0.87")
    
    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
