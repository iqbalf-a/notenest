# ===== STAGE 1: BUILD (bikin file .jar) =====
# Image berisi JDK 21. Diberi nama "builder" untuk dirujuk di stage kedua.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper + pom dulu, lalu download dependency.
# Dipisah supaya kalau kode berubah tapi pom tidak, download tidak diulang (cache layer).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copy sisa kode, lalu build jadi .jar (skip test biar cepat)
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ===== STAGE 2: RUNTIME (jalankan .jar) =====
# JRE saja (tanpa tools build) -> image jauh lebih kecil
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
