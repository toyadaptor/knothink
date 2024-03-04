FROM amd64/clojure:openjdk-17-lein-buster AS build-jar
WORKDIR /knothink
COPY . .
RUN lein uberjar

FROM amd64/amazoncorretto:17-alpine
WORKDIR /knothink

COPY --from=build-jar /knothink/target/knothink-0.1.0-SNAPSHOT-standalone.jar .
COPY --from=build-jar /knothink/docker-entrypoint.sh .

ENTRYPOINT ["/bin/sh", "./docker-entrypoint.sh"]
