# 실행 전용 이미지
# jar는 Gradle로 먼저 빌드를 진행한다 ./gradlew :services:member-service:bootJar
FROM eclipse-temurin:25-jre

ENV TZ=Asia/Seoul
WORKDIR /app

# K8s의 runAsNonRoot가 확인할 수 있도록 숫자 UID로 실행
RUN groupadd -r -g 1001 app && useradd -r -u 1001 -g app app

COPY build/libs/app.jar app.jar

USER 1001:1001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]