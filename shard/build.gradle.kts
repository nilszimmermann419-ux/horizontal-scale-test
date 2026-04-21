plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":proto"))
    implementation(project(":api"))
    implementation(project(":plugin-loader"))
    
    implementation("net.minestom:minestom:${property("minestomVersion")}")
    implementation("io.grpc:grpc-netty:${property("grpcVersion")}")
    implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    implementation("io.grpc:grpc-stub:${property("grpcVersion")}")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    implementation("net.kyori:adventure-api:4.15.0")
    implementation("com.google.code.gson:gson:2.13.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.testcontainers:testcontainers:1.21.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.shardedmc.shard.ShardServer")
}

tasks.test {
    useJUnitPlatform()
}
