# Alfresco-zipkin

This project creates two amps, `alfresco-zipkin-repo.amp` and `alfresco-zipkin-share.amp`.
These amps add zipkin tracing functionality to Alfresco. 
Tracing data will be reported to a zipkin spancollector.

## Building

To build the amps, run `./gradlew build`. 
Artifacts will be available in `{subproject}/build/libs/{subproject}-{version}.amp`, 
with `{subproject}` being either `alfresco-zipkin-repo` or `alfresco-zipkin-share` 

## Usage

For this amp to be useful, a zipkin spancollector must be available. 
You can set one up with the following example `docker-compose-zipkin.yml`. 
More examples can be found in `integration-tests/src/integration-test/resources/compose`. 

```
version: '2.1'

services:
  alfresco:
    environment:
    - GLOBAL_zipkin.collector=http://zipkin:9411/api/v2/spans
    - DB_DRIVER=com.p6spy.engine.spy.P6SpyDriver
    - DB_URL=jdbc:p6spy:postgresql://database:5432/alfresco

  zipkin:
    image: openzipkin/zipkin:2.10.4
    ports:
    - ${COMPOSE_ZIPKIN_TCP_9411:-9411:9411}
```

### Zipkin

The Zipkin service sets up the span collector. 
The environment variable `GLOBAL_zipkin.collector` in the Alfresco service points to the Zipkin service. 
This example assumes an otherwise correctly configured Alfresco service to be composed with this example.

### P6spy

The tracing of the database uses [p6spy](https://github.com/openzipkin/brave/tree/master/instrumentation/p6spy).
The environment variables `DB_DRIVER` and `DB_URL`in the Alfresco service set the DB driver and url for P6spy.
This example assumes on otherwise correctly configured Alfresco service to be composed with this example 
and assumes that the database used is Postgresql.




