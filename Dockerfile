# Build stage
FROM maven:3.9-eclipse-temurin-21 as build
WORKDIR /app
# Copy pom.xml and source code
COPY pom.xml .
COPY ChestsPlusPlus_Main ./ChestsPlusPlus_Main
COPY ChestsPlusPlus_1_21_R1 ./ChestsPlusPlus_1_21_R1
COPY ChestsPlusPlusAPI ./ChestsPlusPlusAPI

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage (your existing configuration)
FROM marctv/minecraft-papermc-server:latest as runtime
LABEL authors="james"

RUN mkdir -p "/data/plugins/ChestsPlusPlus"
RUN echo "update-checker: false" > /data/plugins/ChestsPlusPlus/config.yml

RUN mkdir -p "/data/config"
RUN echo "" > /data/config/paper-global.yml

RUN echo "motd=Development Server" > /data/server.properties
RUN echo "level-type=flat" > /data/server.properties

RUN echo '[{"uuid":"e0e93eb6-2ca4-4ac2-803f-684ce0b69b2c","name":"jameslfc19","level":4,"bypassesPlayerLimit":false}]' > /data/ops.json

# Copy the built JAR file from the build stage
COPY --from=build /app/Server/plugins/ChestsPlusPlus-*.jar /data/plugins/