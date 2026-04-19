plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":proto"))
    
    implementation("io.grpc:grpc-netty:${property("grpcVersion")}")
    implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    implementation("io.grpc:grpc-stub:${property("grpcVersion")}")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    
    // REST API
    implementation("io.javalin:javalin:5.6.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.shardedmc.coordinator.CoordinatorServer")
}

tasks.test {
    useJUnitPlatform()
}
