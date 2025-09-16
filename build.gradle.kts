plugins {
    kotlin("jvm") version "2.0.20"
}

group = "justenter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("com.querydsl:querydsl-jpa:5.0.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}