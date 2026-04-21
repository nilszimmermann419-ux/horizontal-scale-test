plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":shared"))
    implementation("net.minestom:minestom:${property("minestomVersion")}")
    implementation("net.kyori:adventure-api:4.15.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
