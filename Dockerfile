FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
COPY src src

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:21-jre

# eMudhra CA compromise (October 2026, CVE-2026-91403 / CVE-2026-114820):
# stop trusting the emSign roots and the old Comodo "AAA Certificate Services"
# root that cross-signed the compromised intermediates. None of the services we
# call (PVGIS, Google/Firebase, Neon) depend on them. Remove this once the base
# image ships a JDK whose release notes mention JDK-8395690.
RUN keytool -list -v -cacerts -storepass changeit \
      | awk -F': ' '/^Alias name:/{alias=$2} /^Owner:/ && /emSign|AAA Certificate Services/{print alias}' \
      | while IFS= read -r alias; do \
          echo "Removing from cacerts: $alias"; \
          keytool -delete -cacerts -storepass changeit -alias "$alias"; \
        done \
 && ! keytool -list -v -cacerts -storepass changeit | grep -E '^Owner:.*(emSign|AAA Certificate Services)'

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
