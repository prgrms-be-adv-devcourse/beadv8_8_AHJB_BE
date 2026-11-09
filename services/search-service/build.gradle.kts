plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:common-kafka"))
    implementation(project(":libs:event-contracts"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // 검색 저장소 (DB 없이 Elasticsearch만 사용)
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
}
