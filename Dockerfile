# Use lightweight Java 17 runtime
FROM eclipse-temurin:17-jdk-alpine

# Set working directory
WORKDIR /app

# Copy jar into container
COPY target/report-engine-1.0.0.jar app.jar

# Cloud Run sets PORT env variable, default to 8080
ENV PORT=8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]