FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/geoabstraction-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]