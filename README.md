# Alfresco-zipkin

Integrates Alfresco with Zipkin, a distributed tracing system, that gathers timing data to troubleshoot latency problems.

This project creates two amps, `alfresco-zipkin-repo.amp` and `alfresco-zipkin-share.amp`.
These amps add zipkin tracing functionality to Alfresco. Tracing data will be reported to a zipkin spancollector.

## Building

To build the amps, run `./gradlew build`. 
Artifacts will be available in `{subproject}/build/libs/{subproject}-{version}.amp`, 
with `{subproject}` being either `alfresco-zipkin-repo` or `alfresco-zipkin-share` 

## Usage

For this amp to be useful, a zipkin spancollector must be available. 
You can set one up with the following example `docker-compose-zipkin.yml`. 
More examples can be found in `integration-tests/src/integration-test/resources/compose`. 

<a name="compose-example"></a>

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

#### Collector

The Zipkin service sets up the span collector. 
The environment variable `GLOBAL_zipkin.collector` in the Alfresco service points to the Zipkin service. 
This example assumes an otherwise correctly configured Alfresco service to be composed with this example.

#### Enabling tracing on requests

Only a certain percentage of requests will be traced and reported to your Zipkin collector. There are two ways to have your requests traced.

##### 1) alfresco-global.properties

You can alter the sampling rate by setting the following properties in alfresco-global.properties:
```
zipkin.service.alfresco.sampler.rate=1.0
zipkin.service.solr.sampler.rate=1.0
zipkin.service.share.sampler.rate=1.0
```
Or equivalently as environment variables in the docker-compose setup:
```
GLOBAL_zipkin.service.alfresco.sampler.rate=1.0
GLOBAL_zipkin.service.solr.sampler.rate=1.0
GLOBAL_zipkin.service.share.sampler.rate=1.0
```
The value should be set between `0.0` and `1.0`, signifying respectively no traced requests and all requests being traced. For example, if you want 20% of your requests to be traced, put in `0.2`.

##### 2) Chrome browser plugin

There was a [Chrome browser plugin](https://chrome.google.com/webstore/detail/zipkin-browser-extension/jdpmaacocdhbmkppghmgnjmfikeeldfe) made by the Zipkin team that can be used for toggling whether to add the tracing headers to your browser requests and send tracing information to a span collector of your choice. This was very convenient for local development. 

However it seems that it is no longer available on the Chrome Webstore. You might still be able to build it yourself (also for Firefox). [zipkin-browser-extension](https://github.com/openzipkin/zipkin-browser-extension) 


### P6spy

The tracing of the database uses [p6spy](https://github.com/openzipkin/brave/tree/master/instrumentation/p6spy).
The environment variables `DB_DRIVER` and `DB_URL` in the Alfresco service of the docker-compose setup set the DB driver and url for P6spy.
The [example given above](#compose-example) assumes an otherwise correctly configured Alfresco service to be composed with this example and assumes that the database used is Postgresql.




