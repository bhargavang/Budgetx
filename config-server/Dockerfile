FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S budgetx && adduser -S budgetx -G budgetx
COPY target/*.jar app.jar
USER budgetx
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
