FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY KtorCIO-1.jar /app/server.jar

EXPOSE 8000

CMD ["java", "-jar", "/app/server.jar"]