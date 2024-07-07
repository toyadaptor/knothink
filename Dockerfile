FROM amd64/clojure:openjdk-17-lein-buster AS build-jar
WORKDIR /knothink

COPY . .
RUN lein deps
RUN lein uberjar

FROM amd64/amazoncorretto:17-alpine
WORKDIR /knothink

ARG DOCKER_TAG
ENV APP_VERSION=$DOCKER_TAG
RUN echo "Building Docker image version: $APP_VERSION"

COPY --from=build-jar "/knothink/target/knothink-$APP_VERSION-SNAPSHOT-standalone.jar" knothink.jar
COPY --from=build-jar /knothink/docker-entrypoint.sh .

ENTRYPOINT ["/bin/sh", "./docker-entrypoint.sh"]
