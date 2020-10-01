FROM maven:3.6.3-jdk-14 AS build
COPY src /dd/src
COPY pom.xml /dd
RUN mvn -f /dd/pom.xml clean package

FROM openjdk:14.0.2-slim
COPY --from=build /dd/target/dd-backend.jar /dd/dd-backend.jar
RUN apt-get update -y && apt-get upgrade -y
RUN apt-get install tesseract-ocr -y
ENV LC_ALL=C
EXPOSE 8080
ENTRYPOINT ["java","-jar","/dd/dd-backend.jar"]
