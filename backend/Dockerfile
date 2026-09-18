# Build context-nya folder ini (backend/), seperti Dockerfile mana pun:
#
#   docker build -t notenest-api backend
#
# Di Render: Dockerfile Path = backend/Dockerfile, Context = backend

# ===== STAGE 1: BUILD (bikin file .jar) =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Wrapper + pom dulu, lalu download dependency. Dipisah supaya perubahan kode
# tidak ikut membatalkan cache download.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ===== STAGE 2: RUNTIME (jalankan .jar) =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Disetel untuk instance 512 MB / 0.1 CPU:
#   MaxRAMPercentage  - tanpa ini JVM mengira boleh pakai 25% RAM host, bukan container
#   UseSerialGC       - GC paralel butuh CPU yang memang tidak ada di sini
#   Xss512k           - stack per thread dari 1 MB jadi 512 KB, lumayan untuk Tomcat
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
