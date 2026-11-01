# Ubuntu-based image: multi-arch (x86 and ARM, e.g. Oracle Cloud Ampere). The alpine tag is x86-only.
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system budgetx && useradd --system --gid budgetx --no-create-home budgetx
COPY target/*.jar app.jar
USER budgetx
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
