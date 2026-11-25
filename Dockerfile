# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---- runtime stage ----
# TODO (hardening): pin base images by digest once the pipeline is stable.
FROM eclipse-temurin:21-jre
RUN groupadd --system aura && useradd --system --gid aura --no-create-home aura
WORKDIR /app
COPY --from=build /workspace/target/auratrade-*.jar app.jar
USER aura
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
