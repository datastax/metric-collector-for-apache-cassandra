FROM maven:3.6.3-jdk-8-slim as builder

WORKDIR /build

COPY . ./
RUN mvn -ff package -DskipTests

RUN mkdir -p /mcac/lib

RUN cp /build/target/datastax-mcac-agent-0.1.0-SNAPSHOT.jar /mcac/lib
RUN cp -r /build/target/collectd /mcac/lib

