FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/document-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]