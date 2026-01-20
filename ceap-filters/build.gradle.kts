dependencies {
    // Internal dependencies
    implementation(project(":ceap-common"))
    implementation(project(":ceap-models"))
    implementation(project(":ceap-storage"))
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Coroutines for parallel execution
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
