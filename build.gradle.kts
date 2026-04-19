plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.shardedmc"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    
    tasks.withType<JavaCompile> {
        options.release.set(25)
    }
}
