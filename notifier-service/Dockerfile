FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests -DskipITs

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home notifier
USER notifier
WORKDIR /app
COPY --from=build /build/target/notifier-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
