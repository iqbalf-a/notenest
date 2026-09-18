# Build context-nya backend/, BUKAN folder ini - karena modul ini mengompilasi
# source milik auth/user/note yang ada di folder sebelah.
#
#   docker build -f backend/monolith/Dockerfile backend
#
# Di Render: Dockerfile Path = backend/monolith/Dockerfile
#            Docker Build Context Directory = backend

# ===== STAGE 1: BUILD =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build/monolith

# Wrapper + pom dulu, lalu download dependency. Dipisah supaya perubahan kode
# tidak ikut membatalkan cache download.
COPY monolith/.mvn/ .mvn/
COPY monolith/mvnw monolith/mvnw.cmd ./
COPY monolith/pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY monolith/src/ src/

# Hanya src/main/java yang diambil dari ketiga service - resources mereka
# sengaja ditinggal. Kalau application.properties milik auth/user/note ikut
# masuk classpath, konfigurasinya akan bertabrakan dengan milik modul ini.
COPY auth-service/src/main/java/ /build/auth-service/src/main/java/
COPY user-service/src/main/java/ /build/user-service/src/main/java/
COPY note-service/src/main/java/ /build/note-service/src/main/java/

RUN ./mvnw clean package -DskipTests -B

# ===== STAGE 2: RUNTIME =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/monolith/target/*.jar app.jar

# Disetel untuk instance 512 MB / 0.1 CPU:
#   MaxRAMPercentage  - tanpa ini JVM mengira boleh pakai 25% RAM host, bukan container
#   UseSerialGC       - GC paralel butuh CPU yang memang tidak ada di sini
#   Xss512k           - stack per thread dari 1 MB jadi 512 KB, lumayan untuk Tomcat
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
