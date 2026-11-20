FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q -pl worker-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home worker
USER worker
WORKDIR /app
COPY --from=build /build/worker-service/target/worker-service-*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]