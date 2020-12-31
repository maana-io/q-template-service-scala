## Maana Q Service Scala Template


### Building
Standard scala service with SBT using the JavaAppPackaging plugin.

build the container with 
```
sbt docker:publishLocal
```

docker run -p 8080:8080 maana_fast_scheduler:latest

### Service Endpoint
```
http://<hostname>:8080/graphql
```

### Basic Use
query {
  testResolver(person:{
    id: "name",
    name: "Mike"
  }){
    id
    greeting
  }
}
