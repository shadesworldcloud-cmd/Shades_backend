# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render uses PORT env variable
ENV PORT=8080
EXPOSE 8080

# Optimized JVM flags for Render free tier (512MB RAM)
ENTRYPOINT ["java", "-Xmx384m", "-Xms256m", "-XX:+UseSerialGC", "-jar", "app.jar", "--server.port=${PORT}"]
