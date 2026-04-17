# Step 1: Build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Step 2: Run the app
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/report-engine-1.0.0.jar app.jar
CMD ["java", "-jar", "app.jar"]