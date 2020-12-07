## Fast Scheduler 


### Building
Standard scala service with SBT using the JavaAppPackaging plugin.

build the container with 
```
sbt docker:publishLocal

docker run -p 8080:8080 maana_fast_scheduler:latest:latest

### Service Endpoint
```
http://<hostname>:8080/graphql
```


### Service Inputs

##Vessels

 { 
	id: "12345"
  	name: "mike's vessel"
  	dimensions:{
	  id: "999"
	  beam: 10
	  overallLength: 10
	  aftParallelBodyDistance: 10
	  forwardParallelBodyDistance: 10
  	}
    speedCapabilities: {
        id: "999"
      ladenEconomicSpeed: 10
      ladenMaxSpeed: 10
      ballastEconomicSpeed: 10
      ballastMaxSpeed: 10

    }
    carryingCapacity:{
       id: "999"
      fuelCapacity: 1000
      diesalCapacity: 1000
      gasOilCapacity: 1000
    }
    currentStatus: {
        id: "999"
        availableFrom: "2019-12-16T00:00:00.000Z"
        lastKnownPort: "SAYNB" 
        lastProduct: "Raffinates"
        startingFuel: 616.0
    }
    clean: "C"
    details: {
		  id: "vessel details"
		  charteringCost: 1000
		  contractExpiration: "2021-11-16"
		  scnt: 50000.0
		  sizeCategory: "LR"
		 totalProductCapacity: 10000
		 cleaningTimeMultiplier: 1
		 cargoPumpingRateM3PerS: 20
    	imoClass: "mikes class"
 	}
    portRestrictions: []
    bunkerRequirements:  {
        id: "999"
        laden_speed_11: 10
        laden_speed_12: 10
        laden_speed_12_5: 10
        laden_speed_13: 10
        laden_speed_13_5: 10
        laden_speed_14: 10
        laden_speed_14_5: 10
        laden_speed_15: 10
        ballast_speed_11: 10
        ballast_speed_12: 10
        ballast_speed_12_5: 10
        ballast_speed_13: 10
        ballast_speed_13_5: 10
        ballast_speed_14: 10
        ballast_speed_14_5: 10
        ballast_speed_15: 10
        no_eca_cold_cleaning: 10.0
        no_eca_hot_cleaning: 10.0
    }
        unavailableTimes:[]
    }

### Service configuration


The vessel data required will be provide at Input








If they are not provided they default to localhost with the associated default port.

If the services are not available at start up, the service will wait 10s and terminate itself,
Docker will then restart the service eventually leading to a stable state. This mechanism used instead of compose dependencies
because the services it is dependent on are not immediately available after they start, and it also works in swarm or K8's or any
other orchestration framework that can be configured to restart failing containers.

It's also possible to specify if the service uses a global distance cache for distance calculations, or one per query, it defaults to the former.
It requires changing the application.conf file or overriding the config value to make the change. 



## Query for the Schedules Resolver
query {
  schedules(
    date:"2019-12-16T00:00:00.000Z",
    vessels: [
      { id: "9189110", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 616.0, lastProduct: "", location: "SAYNB" }
      { id: "9251274", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 641.7365796431, lastProduct: "", location: "SAYNB" }
      { id: "9292826", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 220.5, lastProduct: "", location: "SAYNB" }
      { id: "9292838", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1947.6133863762, lastProduct: "", location: "SAJED" }
      { id: "9323596", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1849.5554983669, lastProduct: "", location: "SAGIZ" }
      { id: "9324497", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1943.5571692663, lastProduct: "", location: "SAJED" }
      { id: "9409467", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 2156.2327866218, lastProduct: "", location: "SAJED" }
      { id: "9413016", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 414.0, lastProduct: "", location: "GRAGT" }
      { id: "9459096", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 140.6, lastProduct: "", location: "SAYNB" }
      { id: "9462354", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 2021.04830573, lastProduct: "", location: "SAJED" }
      { id: "9486922", availableFrom: "2019-12-19T00:26:35.000Z", startingFuel: 175.0, lastProduct: "", location: "GRAGT" }
      { id: "9486934", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 258.0, lastProduct: "", location: "EGSUZ" }
      { id: "9487483", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1871.6182223748, lastProduct: "", location: "SAJUB" }
      { id: "9580417", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 276.2, lastProduct: "", location: "SAYNB" }
      { id: "9655602", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1217.5606120919, lastProduct: "", location: "SARTA" }
      { id: "9681405", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1354.5942837286, lastProduct: "", location: "SADHU" }
      { id: "9685190", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1000.0, lastProduct: "", location: "SAYNB" }
      { id: "9685205", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 982.9792789506, lastProduct: "", location: "SADHU" }
      { id: "9688336", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1151.8856820434, lastProduct: "", location: "SAGIZ" }
      { id: "9688348", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 90.6, lastProduct: "", location: "SAYNB" }
      { id: "9705885", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1218.5164507065, lastProduct: "", location: "SAYNB" }
      { id: "9705897", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 1195.9759004954, lastProduct: "", location: "JOAQJ" }
      { id: "9705902", availableFrom: "2019-12-16T00:00:00.000Z", startingFuel: 984.8816329773, lastProduct: "", location: "KWMEA" }
    ]
    requirements: [
      {id: "CIT20191250", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 79493.65, location: "SAJED", valid: {startDate: "2019-12-27T00:00:00.000Z", endDate: "2019-12-28T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 79493.65, location: "SARAB", valid: {startDate: "2019-12-25T00:00:00.000Z", endDate: "2019-12-26T23:59:59.000Z" } }] }
      {id: "CIT20191261", cleanStatus: "c", shorts: [{ product: "naphtha", quantity: 47696.19, location: "SAJUB", valid: {startDate: "2020-01-01T00:00:00.000Z", endDate: "2020-01-02T23:59:59.000Z" } }], longs: [{ product: "naphtha", quantity: 47696.19, location: "SARTA", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }] }
      {id: "533218", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAGIZ", valid: {startDate: "2019-12-17T00:00:00.000Z", endDate: "2019-12-18T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-13T00:00:00.000Z", endDate: "2019-12-14T23:59:59.000Z" } }] }
      {id: "CIT20191257", cleanStatus: "c", shorts: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAJED", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }], longs: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SARAB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }] }
      {id: "CIT20191214", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "AEFJR", valid: {startDate: "2019-12-12T00:00:00.000Z", endDate: "2019-12-14T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2019-12-04T00:00:00.000Z", endDate: "2019-12-05T23:59:59.000Z" } }] }
      {id: "CIT20191253", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 39746.825, location: "SAYNB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 39746.825, location: "SAYNB", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }] }
      {id: "CIT20191240", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAGIZ", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-22T00:00:00.000Z", endDate: "2019-12-23T23:59:59.000Z" } }] }
      {id: "CIT20191225", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "AEFJR", valid: {startDate: "2019-12-23T00:00:00.000Z", endDate: "2019-12-26T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2019-12-14T00:00:00.000Z", endDate: "2019-12-15T23:59:59.000Z" } }] }
      {id: "CIT20191119", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.9679746, location: "AEFJR", valid: {startDate: "2019-11-26T00:00:00.000Z", endDate: "2019-11-27T00:00:00.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.9679746, location: "SAYNB", valid: {startDate: "2019-11-22T00:00:00.000Z", endDate: "2019-11-24T00:00:00.000Z" } }] }
      {id: "CIT20191259", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAGIZ", valid: {startDate: "2020-01-02T00:00:00.000Z", endDate: "2020-01-06T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2020-01-01T23:59:59.000Z" } }] }
      {id: "CIT20191278", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAGIZ", valid: {startDate: "2019-12-29T00:00:00.000Z", endDate: "2020-01-03T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }] }
      {id: "CIT20191252", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAGIZ", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }] }
      {id: "533402", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 47696.19, location: "EGSUZ", valid: {startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "SAASQ", valid: {startDate: "2019-12-21T00:00:00.000Z", endDate: "2019-12-22T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 47696.19, location: "EGAIS", valid: {startDate: "2019-12-11T00:00:00.000Z", endDate: "2019-12-12T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "EGSUZ", valid: {startDate: "2019-12-19T00:00:00.000Z", endDate: "2019-12-20T23:59:59.000Z" } }] }
      {id: "533223", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 79493.65, location: "SAJED", valid: {startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2019-12-16T00:00:00.000Z", endDate: "2019-12-17T23:59:59.000Z" } }] }
      {id: "CIT20191270", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAJED", valid: {startDate: "2019-12-22T00:00:00.000Z", endDate: "2019-12-23T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SARAB", valid: {startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }] }
      {id: "CIT20191256", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAJED", valid: {startDate: "2020-01-01T00:00:00.000Z", endDate: "2020-01-04T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }] }
      {id: "533172", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SADHU", valid: {startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-28T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-15T00:00:00.000Z", endDate: "2019-12-16T23:59:59.000Z" } }] }
      {id: "CIT20191263", cleanStatus: "c", shorts: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAYNB", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }], longs: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAYNB", valid: {startDate: "2019-12-27T00:00:00.000Z", endDate: "2019-12-28T23:59:59.000Z" } }] }
      {id: "CIT20191230", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAGIZ", valid: {startDate: "2019-12-22T00:00:00.000Z", endDate: "2019-12-26T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-19T00:00:00.000Z", endDate: "2019-12-20T23:59:59.000Z" } }] }
      {id: "533025", cleanStatus: "c", shorts: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAYNB", valid: {startDate: "2019-12-16T00:00:00.000Z", endDate: "2019-12-17T23:59:59.000Z" } }], longs: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAYNB", valid: {startDate: "2019-12-14T00:00:00.000Z", endDate: "2019-12-15T23:59:59.000Z" } }] }
      {id: "530714", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 95392.38, location: "SARAB", valid: {startDate: "2019-12-15T00:00:00.000Z", endDate: "2019-12-16T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 95392.38, location: "GRAGT", valid: {startDate: "2019-12-07T00:00:00.000Z", endDate: "2019-12-10T23:59:59.000Z" } }] }
      {id: "CIT20191118", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.8089873, location: "AEFJR", valid: {startDate: "2019-11-29T00:00:00.000Z", endDate: "2019-11-30T00:00:00.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.8089873, location: "SAYNB", valid: {startDate: "2019-11-22T00:00:00.000Z", endDate: "2019-11-23T00:00:00.000Z" } }] }
      {id: "CIT20191249-41", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 47696.19, location: "EGSUZ", valid: {startDate: "2019-12-25T00:00:00.000Z", endDate: "2019-12-26T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "EGAIS", valid: {startDate: "2019-12-23T00:00:00.000Z", endDate: "2019-12-24T23:59:59.000Z" } }] }
      {id: "533113", cleanStatus: "c", shorts: [{ product: "naphtha", quantity: 47696.19, location: "SAJUB", valid: {startDate: "2019-12-15T00:00:00.000Z", endDate: "2019-12-16T23:59:59.000Z" } }], longs: [{ product: "naphtha", quantity: 47696.19, location: "SARTA", valid: {startDate: "2019-12-13T00:00:00.000Z", endDate: "2019-12-14T23:59:59.000Z" } }] }
      {id: "533154", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAGIZ", valid: {startDate: "2019-12-16T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-12T00:00:00.000Z", endDate: "2019-12-13T23:59:59.000Z" } }] }
      {id: "CIT20191248", cleanStatus: "c", shorts: [{ product: "naphtha", quantity: 47696.19, location: "SAJUB", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }], longs: [{ product: "naphtha", quantity: 47696.19, location: "SARTA", valid: {startDate: "2019-12-24T00:00:00.000Z", endDate: "2019-12-25T23:59:59.000Z" } }] }
      {id: "CIT20191273", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 79493.65, location: "SAJED", valid: {startDate: "2020-01-02T00:00:00.000Z", endDate: "2020-01-03T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2020-01-01T00:00:00.000Z", endDate: "2020-01-02T23:59:59.000Z" } }] }
      {id: "CIT20191234", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAJED", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2019-12-20T00:00:00.000Z", endDate: "2019-12-21T23:59:59.000Z" } }] }
      {id: "CIT20191262", cleanStatus: "c", shorts: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAJED", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }], longs: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAYNB", valid: {startDate: "2019-12-24T00:00:00.000Z", endDate: "2019-12-25T23:59:59.000Z" } }] }
      {id: "CIT20191245", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SADHU", valid: {startDate: "2019-12-27T00:00:00.000Z", endDate: "2020-01-05T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-24T00:00:00.000Z", endDate: "2019-12-25T23:59:59.000Z" } }] }
      {id: "CIT20191251", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAGIZ", valid: {startDate: "2019-12-29T00:00:00.000Z", endDate: "2020-01-03T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 42926.570999999996, location: "SAYNB", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }] }
      {id: "CIT20191242", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 47696.19, location: "SAGIZ", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-30T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-23T00:00:00.000Z", endDate: "2019-12-24T23:59:59.000Z" } }] }
      {id: "530715", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 95392.38, location: "SAASQ", valid: {startDate: "2019-12-31T00:00:00.000Z", endDate: "2020-01-01T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 95392.38, location: "GRAGT", valid: {startDate: "2019-12-20T00:00:00.000Z", endDate: "2019-12-22T23:59:59.000Z" } }] }
      {id: "CIT20191255", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAGIZ", valid: {startDate: "2019-12-31T00:00:00.000Z", endDate: "2020-01-01T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-27T00:00:00.000Z", endDate: "2019-12-28T23:59:59.000Z" } }] }
      {id: "CIT20191260", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAGIZ", valid: {startDate: "2020-01-04T00:00:00.000Z", endDate: "2020-01-05T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 47696.19, location: "SARAB", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }] }
      {id: "CIT20191277-78", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 47696.19, location: "EGSUZ", valid: {startDate: "2020-01-04T00:00:00.000Z", endDate: "2020-01-05T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2020-01-09T00:00:00.000Z", endDate: "2020-01-10T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 47696.19, location: "EGAIS", valid: {startDate: "2020-01-03T00:00:00.000Z", endDate: "2020-01-03T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "EGSUZ", valid: {startDate: "2020-01-04T00:00:00.000Z", endDate: "2020-01-05T23:59:59.000Z" } }] }
      {id: "CIT20191231", cleanStatus: "c", shorts: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SAYNB", valid: {startDate: "2019-12-20T00:00:00.000Z", endDate: "2019-12-21T23:59:59.000Z" } }], longs: [{ product: "aviation kerosenes", quantity: 34977.206, location: "SARAB", valid: {startDate: "2019-12-19T00:00:00.000Z", endDate: "2019-12-20T23:59:59.000Z" } }] }
      {id: "CIT20191271", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-27T00:00:00.000Z", endDate: "2019-12-28T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 47696.19, location: "SARAB", valid: {startDate: "2019-12-25T00:00:00.000Z", endDate: "2019-12-26T23:59:59.000Z" } }] }
      {id: "CIT20191272", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 39746.825, location: "SAYNB", valid: {startDate: "2020-01-01T00:00:00.000Z", endDate: "2020-01-02T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 39746.825, location: "SARAB", valid: {startDate: "2019-12-30T00:00:00.000Z", endDate: "2019-12-31T23:59:59.000Z" } }] }
      {id: "CIT20191235", cleanStatus: "c", shorts: [{ product: "mtbe", quantity: 31797.46, location: "SARTA", valid: {startDate: "2019-12-22T00:00:00.000Z", endDate: "2019-12-23T23:59:59.000Z" } }], longs: [{ product: "mtbe", quantity: 31797.46, location: "SAJUB", valid: {startDate: "2019-12-20T00:00:00.000Z", endDate: "2019-12-21T23:59:59.000Z" } }] }
      {id: "CIT20191258", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 39746.825, location: "SAYNB", valid: {startDate: "2019-12-31T00:00:00.000Z", endDate: "2020-01-03T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 39746.825, location: "SARAB", valid: {startDate: "2019-12-29T00:00:00.000Z", endDate: "2019-12-30T23:59:59.000Z" } }] }
      {id: "CIT20191232", cleanStatus: "c", shorts: [{ product: "naphtha", quantity: 47696.19, location: "SAJUB", valid: {startDate: "2019-12-21T00:00:00.000Z", endDate: "2019-12-22T23:59:59.000Z" } }], longs: [{ product: "naphtha", quantity: 47696.19, location: "SARTA", valid: {startDate: "2019-12-19T00:00:00.000Z", endDate: "2019-12-20T23:59:59.000Z" } }] }
      {id: "1042786", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 47696.19, location: "SAJED", valid: {startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-16T00:00:00.000Z", endDate: "2019-12-17T23:59:59.000Z" } }] }
      {id: "CIT20191229", cleanStatus: "c", shorts: [{ product: "dyed gas oil", quantity: 47696.19, location: "SADHU", valid: {startDate: "2019-12-20T00:00:00.000Z", endDate: "2019-12-21T23:59:59.000Z" } }], longs: [{ product: "dyed gas oil", quantity: 47696.19, location: "SAYNB", valid: {startDate: "2019-12-17T00:00:00.000Z", endDate: "2019-12-18T23:59:59.000Z" } }] }
      {id: "CIT20191254", cleanStatus: "c", shorts: [{ product: "mtbe", quantity: 31797.46, location: "SARTA", valid: {startDate: "2019-12-29T00:00:00.000Z", endDate: "2019-12-30T23:59:59.000Z" } }], longs: [{ product: "mtbe", quantity: 31797.46, location: "SAJUB", valid: {startDate: "2019-12-26T00:00:00.000Z", endDate: "2019-12-27T23:59:59.000Z" } }] }
      {id: "CIT20191265", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 39746.825, location: "SAYNB", valid: {startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 39746.825, location: "SARAB", valid: {startDate: "2019-12-16T00:00:00.000Z", endDate: "2019-12-17T23:59:59.000Z" } }] }
      {id: "CIT20191243-37", cleanStatus: "d", shorts: [{ product: "heavy fuel oil", quantity: 47696.19, location: "SARAB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "SARAB", valid: {startDate: "2019-12-28T00:00:00.000Z", endDate: "2019-12-29T23:59:59.000Z" } }], longs: [{ product: "heavy fuel oil", quantity: 47696.19, location: "EGSUZ", valid: {startDate: "2019-12-24T00:00:00.000Z", endDate: "2019-12-25T23:59:59.000Z" } }, { product: "heavy fuel oil", quantity: 47696.19, location: "EGAIS", valid: {startDate: "2019-12-21T00:00:00.000Z", endDate: "2019-12-22T23:59:59.000Z" } }] }
      {id: "CIT20191246", cleanStatus: "c", shorts: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "AEFJR", valid: {startDate: "2020-01-02T00:00:00.000Z", endDate: "2020-01-05T23:59:59.000Z" } }], longs: [{ product: "unleaded motor spirit", quantity: 79493.65, location: "SAYNB", valid: {startDate: "2019-12-24T00:00:00.000Z", endDate: "2019-12-25T23:59:59.000Z" } }] }
    ]
  )
}
