FROM openjdk:8-alpine

COPY target/uberjar/cyberme.jar /cyberme/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/cyberme/app.jar"]
