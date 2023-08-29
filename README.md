# History
This microservice manages the user actions for the `ui` frontend application. It is a Spring Boot application that is build upon MongoDB and Webflux.

## Build
```sh
./gradlew build
```

## Run
```sh
./gradlew bootRun
```

## Test
```sh
./gradlew test                                                                        # run all tests
./gradlew test --tests ActionsControllerIntegrationTest                               # run all tests in file
./gradlew test --tests ActionsControllerIntegrationTest.thatActionCanBeSaved          # run individual test in file
./gradlew test --tests ActionsControllerIntegrationTest.thatActionCanBeSaved --debug  # run individual test in file with debug enabled
```

## Development
You can run the service locally for evaluation or development purposes using the provided `docker-compose.yml` file in the development folder. This will launch a Keycloak, a Postgres and a Mongo db in the background.

**Prerequisites:** Running local docker daemon and a docker cli

To start the service execute the `run.sh` in the development folder:
```sh
development/run.sh
```

Example request against the preferences service can be run in your preferred IDE with the `request.http file from the development folder.

You can access the Keycloak UI via browser.
URL: http://localhost:8080
**username:** admin  
**password:** password

To stop the preferences service, Keycloak and the databases run:
```sh
development/stop.sh
```
