plugins {
    java
    application
}

group = "com.httpserver"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

application {
    mainClass.set("com.httpserver.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Build a fat JAR for easy distribution
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.httpserver.Main"
    }
}
