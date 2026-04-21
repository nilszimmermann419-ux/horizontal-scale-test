plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":proto"))
    implementation(project(":api"))
    implementation(project(":coordinator"))
    implementation(project(":shard"))
    implementation(project(":plugin-loader"))
    
    // Minestom for tests
    implementation("net.minestom:minestom:${property("minestomVersion")}")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.testcontainers:testcontainers:1.21.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
