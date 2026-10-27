FROM eclipse-temurin:21-jre AS java-runtime

FROM debian:bookworm-slim

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENV TZ=Asia/Kolkata
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

# Playwright requires a glibc-based Chromium runtime. Debian provides a real
# Chromium binary; Ubuntu's package is a Snap wrapper and cannot run in Docker.
RUN rm -f /etc/apt/sources.list.d/debian.sources \
    && printf 'deb http://ftp.de.debian.org/debian bookworm main\ndeb http://ftp.de.debian.org/debian bookworm-updates main\ndeb http://ftp.de.debian.org/debian-security bookworm-security main\n' > /etc/apt/sources.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        chromium \
        fonts-liberation \
        curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java-runtime /opt/java/openjdk ${JAVA_HOME}

EXPOSE 8080

ARG BRANCH_NAME
ARG JAR_FILE=target/prolt-operations-*.jar

COPY ${JAR_FILE} app.jar

ENV MaxRAM "${MaxRAM:-1g}"

ENTRYPOINT java -XX:MaxRAM=$MaxRAM $JAVA_OPTS -jar /app.jar
