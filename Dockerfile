# ---------- Stage 1: Build ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

#COPY lib/ZanableShared-0.1.jar /tmp/
#RUN mvn install:install-file \
#      -Dfile=/tmp/ZanableShared-0.1.jar \
#      -DgroupId=com.zanable.shared \
#      -DartifactId=ZanableShared \
#      -Dversion=0.1 \
#      -Dpackaging=jar
#RUN mvn dependency:go-offline -B

# Install local dependency
#COPY settings.xml /root/.m2/settings.xml

COPY lib/ZanableShared-0.1.jar /tmp/
RUN mvn install:install-file \
      -Dfile=/tmp/ZanableShared-0.1.jar \
      -DgroupId=com.zanable.shared \
      -DartifactId=ZanableShared \
      -Dversion=0.1 \
      -Dpackaging=jar
#RUN --mount=type=cache,target=/root/.m2 \
#    mvn -B -q -ntp org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file \
#      -Dfile=/tmp/ZanableShared-0.1.jar \
#      -DgroupId=com.zanable.shared \
#      -DartifactId=ZanableShared \
#      -Dversion=0.1 \
#      -Dpackaging=jar

COPY pom.xml .
# Download deps
RUN mvn dependency:go-offline -B
#RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp dependency:go-offline

COPY src ./src
RUN mvn clean install -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true
#RUN mvn clean package -P autoInstallPackage -DskipTests
#RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
