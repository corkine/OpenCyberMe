FROM openjdk:8-alpine

COPY target/uberjar/icemanager.jar /icemanager/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/icemanager/app.jar"]
