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
    // 경매 상세 공용 부분 캐시 (#557). 버전은 Spring Boot가 관리
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // 상품 이미지 저장소(S3)
    implementation(platform("software.amazon.awssdk:bom:2.57.0"))
    implementation("software.amazon.awssdk:s3")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
// 동시성 정합성 테스트 (@Tag("concurrency"), Docker 필요). 기본 test·CI에서는 제외
// 실행: ./gradlew :services:market-service:concurrencyTest -PlockLabel=v0-baseline [--tests '*.concurrency.English*']
// 결과: build/concurrency-results/{lockLabel}.md, build/reports/tests/concurrencyTest/index.html
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("concurrency")
    }
}

val concurrencyTest by tasks.registering(Test::class) {
    description = "동시성 정합성 테스트 (Testcontainers PostgreSQL)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("concurrency")
    }
    systemProperty("lockLabel", (project.findProperty("lockLabel") ?: "local").toString())
    systemProperty("concurrency.resultDir", layout.buildDirectory.dir("concurrency-results").get().asFile.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
    shouldRunAfter(tasks.named("test"))
}
