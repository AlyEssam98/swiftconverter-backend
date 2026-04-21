# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml and download dependencies to cache them
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn package -DskipTests -o -B

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# JVM Optimizations for fast startup and low memory footprint (Railway Free Tier)
# -XX:TieredStopAtLevel=1: Speeds up startup by limiting C2 compiler
# -Xverify:none: Disables bytecode verification (safe for internal containers)
# -Xms128m -Xmx384m: Explicit memory limits to prevent OOM on free tier
ENTRYPOINT ["java", \
            "-XX:TieredStopAtLevel=1", \
            "-Xverify:none", \
            "-Xms128m", \
            "-Xmx384m", \
            "-XX:+UseG1GC", \
            "-jar", "app.jar"]
