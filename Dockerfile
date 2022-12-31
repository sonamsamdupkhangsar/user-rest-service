FROM maven:3-openjdk-17-slim as build

WORKDIR /app

COPY pom.xml settings.xml ./
COPY src ./src

# use exec shell form to access secret variable as exported env variable
RUN --mount=type=secret,id=PERSONAL_ACCESS_TOKEN \
   export PERSONAL_ACCESS_TOKEN=$(cat /run/secrets/PERSONAL_ACCESS_TOKEN) && \
   mvn -s settings.xml clean install

FROM openjdk:17
WORKDIR /app
COPY --from=build /app/target/user-rest-service-1.0-SNAPSHOT.jar /app/user-rest-service.jar
EXPOSE 8080

ENTRYPOINT [ "java", "-jar", "/app/user-rest-service.jar"]

LABEL org.opencontainers.image.source https://github.com/sonamsamdupkhangsar/user-rest-service