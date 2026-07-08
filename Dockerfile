FROM nexus3.onap.org:10001/eclipse-temurin:25 as builder
COPY . ./history
WORKDIR /history
RUN ./gradlew assemble

FROM nexus3.onap.org:10001/eclipse-temurin:25-jre-alpine
USER nobody
ARG JAR_FILE=/history/app/build/libs/app.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9002
# Java 25 AOT cache (JEP 514/515): a training run records the class graph + JIT profile and exits
# after Spring context refresh. Production start loads the cache and skips that warmup.
# `|| true` keeps the image buildable if the training run cannot reach a DB/Keycloak at build time.
RUN java -XX:AOTCacheOutput=/tmp/app.aot -Dspring.context.exit=onRefresh -jar app.jar || true
ENTRYPOINT [ "java","-XX:AOTCache=/tmp/app.aot","-jar","app.jar" ]
