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
    
    // REST API - using Javalin instead of Vert.x
    implementation("io.javalin:javalin:6.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    
    // Metrics
    implementation("io.micrometer:micrometer-core:1.14.6")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.6")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.testcontainers:testcontainers:1.21.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.shardedmc.coordinator.ProductionCoordinatorServer")
}

tasks.test {
    useJUnitPlatform()
}
