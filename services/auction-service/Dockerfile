# 빌드
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 멀티 모듈 빌드에 필요한 것: wrapper, 루트 설정, 공통 모듈, 서비스들
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY libs ./libs
COPY services ./services

RUN chmod +x gradlew && ./gradlew :services:auction-service:bootJar --no-daemon

# 실행
FROM eclipse-temurin:25-jre
ENV TZ=Asia/Seoul
WORKDIR /app

# K8s의 runAsNonRoot가 확인할 수 있도록 숫자 UID로 실행
RUN groupadd -r -g 1001 app && useradd -r -u 1001 -g app app
COPY --from=build /workspace/services/auction-service/build/libs/app.jar app.jar

USER 1001:1001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]