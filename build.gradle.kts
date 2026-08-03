plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

version = "0.1.0"
group = "dev.heitorrosa"

base {
    archivesName.set("packconverter")
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
