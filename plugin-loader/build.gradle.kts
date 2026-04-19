plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":shared"))
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
