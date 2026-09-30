# syntax=docker/dockerfile:1

# ------------------------------------------------------------------
# build stage - full JDK + maven, throws away everything not needed
# ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies on their own layer, so editing source files
# does not re-download the internet on every rebuild.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ------------------------------------------------------------------
# run stage - JRE only, no compiler, no build tools
# ------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]