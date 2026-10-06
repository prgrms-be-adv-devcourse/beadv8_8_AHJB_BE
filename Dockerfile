# 1단계: 빌드 스테이지 (Gradle wrapper로 실행 가능한 JAR 생성)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 빌드에 필요한 파일 복사 (wrapper, 설정 파일, 소스)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

# gradlew 실행 권한 부여 후 실행용 jar만 빌드 (테스트는 CI에서 별도 실행)
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon


# 2단계: 실행 스테이지 (최종 이미지 생성)
FROM eclipse-temurin:25-jre
WORKDIR /app


# 1단계(build)에서 생성된 jar 파일만 가져오기
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]