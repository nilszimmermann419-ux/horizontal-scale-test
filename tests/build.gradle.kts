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
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
