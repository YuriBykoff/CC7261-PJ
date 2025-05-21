ARG GRADLE_VERSION=8.6
ARG JDK_VERSION=17
FROM gradle:${GRADLE_VERSION}-jdk${JDK_VERSION} AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./ 
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY src ./src
COPY src/main/proto ./src/main/proto
RUN chmod +x ./gradlew
RUN ./gradlew build -x test --no-daemon
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080 
EXPOSE 9090 
ENTRYPOINT ["java", "-jar", "/app/app.jar"] 