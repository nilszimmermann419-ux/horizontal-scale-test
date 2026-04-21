plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.shardedmc"
version = "2.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.spongepowered.org/maven")
}

dependencies {
    // Minestom
    implementation("net.minestom:minestom-snapshots:84a34e623c")

    // gRPC
    implementation("io.grpc:grpc-netty-shaded:1.61.0")
    implementation("io.grpc:grpc-protobuf:1.61.0")
    implementation("io.grpc:grpc-stub:1.61.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // NATS
    implementation("io.nats:jnats:2.17.3")

    // Redis (Lettuce)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // MinIO
    implementation("io.minio:minio:8.5.7")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.11")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.shardedmc.shard.ShardServer")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
}

// Ensure shadow jar is built when running `gradle build`
tasks.build {
    dependsOn(tasks.shadowJar)
}
