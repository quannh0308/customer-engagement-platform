dependencies {
    // Internal dependencies
    implementation(project(":ceap-common"))
    implementation(project(":ceap-models"))
    
    // Test dependencies
    testImplementation(project(":ceap-models"))

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.26"))
    implementation("software.amazon.awssdk:sns")
    implementation("software.amazon.awssdk:ses")
}
