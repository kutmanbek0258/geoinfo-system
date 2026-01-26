FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/stream-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]