dependencies {
    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.26"))
    implementation("software.amazon.awssdk:cloudwatch")

    // JSON Processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Utilities
    implementation("org.apache.commons:commons-lang3:3.14.0")
}
