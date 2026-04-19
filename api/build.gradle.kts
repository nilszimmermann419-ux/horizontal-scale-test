plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":shared"))
    implementation("net.minestom:minestom-snapshots:${property("minestomVersion")}")
    implementation("net.kyori:adventure-api:4.15.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
