FROM eclipse-temurin:17 as builder
COPY . ./history
WORKDIR /history
RUN ./gradlew assemble

FROM eclipse-temurin:17-jre-alpine
USER nobody
ARG JAR_FILE=/history/app/build/libs/app.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]
