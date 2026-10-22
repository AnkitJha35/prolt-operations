FROM eclipse-temurin:21-jre-alpine

ENV TZ=Asia/Kolkata

EXPOSE 8080

ARG BRANCH_NAME
ARG JAR_FILE=target/prolt-operations-*.jar

COPY ${JAR_FILE} app.jar

ENV MaxRAM "$MaxRAM"

ENTRYPOINT java -XX:MaxRAM=$MaxRAM $JAVA_OPTS -jar /app.jar
