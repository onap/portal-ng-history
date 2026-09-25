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
# Nothing is reachable at build time, so the training run gets a dummy value for every required
# ${PLACEHOLDER} in application.yml and skips the startup steps that need a PostgreSQL connection.
RUN unzip -p app.jar BOOT-INF/classes/application.yml \
      | grep -oE '\$\{[A-Z0-9_]+\}' | tr -d '${}' | sort -u \
      | awk '{ print $0 "=" ($0 ~ /_PORT$/ ? "1" : ($0 ~ /_HOST$/ ? "localhost" : ($0 ~ /_URL$/ ? "http://localhost" : "aot"))) }' \
      > /tmp/aot-training.properties \
 && java -XX:AOTCacheOutput=/tmp/app.aot -Dspring.context.exit=onRefresh \
      -Dspring.config.additional-location=file:/tmp/aot-training.properties \
      -Dspring.liquibase.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar app.jar \
 && rm /tmp/aot-training.properties \
 && test -s /tmp/app.aot && ls -l /tmp/app.aot
ENTRYPOINT [ "java","-XX:AOTCache=/tmp/app.aot","-jar","app.jar" ]
