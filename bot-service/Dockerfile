FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q -pl bot-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home bot
USER bot
WORKDIR /app
COPY --from=build /build/bot-service/target/bot-service-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]