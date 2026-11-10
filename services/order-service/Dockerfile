# 빌드
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY libs ./libs
COPY services ./services

RUN chmod +x gradlew && ./gradlew :services:order-service:bootJar --no-daemon

# 실행
FROM eclipse-temurin:25-jre
ENV TZ=Asia/Seoul
WORKDIR /app

RUN groupadd -r -g 1001 app && useradd -r -u 1001 -g app app

COPY --from=build /workspace/services/order-service/build/libs/app.jar app.jar

USER 1001:1001

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]