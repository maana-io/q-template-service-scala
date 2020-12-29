## Fast Scheduler 


### Building
Standard scala service with SBT using the JavaAppPackaging plugin.

build the container with 
```
sbt docker:publishLocal

docker run -p 8080:8080 maana_fast_scheduler:latest

### Service Endpoint
```
http://<hostname>:8080/graphql
```


### Service Inputs

VesselWithQ88AndStatus
Requirements Input


### Service configuration

The vessel data required will be provide at Input

If they are not provided they default to localhost with the associated default port.

If the services are not available at start up, the service will wait 10s and terminate itself,
Docker will then restart the service eventually leading to a stable state. This mechanism used instead of compose dependencies
because the services it is dependent on are not immediately available after they start, and it also works in swarm or K8's or any
other orchestration framework that can be configured to restart failing containers.

It's also possible to specify if the service uses a global distance cache for distance calculations, or one per query, it defaults to the former.
It requires changing the application.conf file or overriding the config value to make the change. 



query {
  schedules(
    date:"2019-12-16T00:00:00.000Z",
    vessels: [
      {
      id: "9688348"
          vessel: {
              id: "9688348"
              name: "NCC Wafa"
            	isParked:false
              clean: "c"
              details: {
                  id: "12345"
                  charteringCost: 12000
                  contractExpiration: "2021-11-16"
                  sizeCategory: "LR"
                  cleaningTimeMultiplier: 1
              }
              portRestrictions: []
            	bunkerRequirements:  {
                id: "9688348"
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
             currentStatus: {
                id: "9688348"
                availableFrom: "2019-12-16T00:00:00.000Z"
                lastKnownPort: "SAYNB" 
                lastProduct: ""
                startingFuel: 200
        		}

      }
      q88Vessel: {
        id: "9688348"
        name: "NCC Wafa"
        totalProductCapacity: 52434.21
        scnt:27515.13,
        imoClass:"2,3"
        cleaningTimeMultiplier:0.1
        cargoPumpingRateM3PerS:2700        
        dimensions:{
          id:"9688348"
          beam: 32.24
          overallLength: 183.12
          aftParallelBodyDistance: 55.899
          forwardParallelBodyDistance: 34.32
        }
        speedCapabilities: {
          id: "9688348"
          ladenEconomicSpeed: 12.5
          ladenMaxSpeed: 14
          ballastEconomicSpeed: 13
          ballastMaxSpeed: 14

        }
        carryingCapacity:{
          id: "9688348"
          fuelCapacity: 1173.5
          diesalCapacity: 490.8
          gasOilCapacity: 36
        }
      }
    
      }]
    requirements: [
      {id: "CIT20191250", cleanStatus: "c", 
        longs: [{id: "CIT20191250", product: "dyed gas oil", quantity: 47696.1885, location: "SAYNB", valid: {id:"CIT20191250",startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }] 
        shorts: [{ id:"CIT20191250",product: "dyed gas oil", quantity: 47696.1885, location: "SAGIZ", valid: {id:"CIT20191250", startDate: "2019-12-22T00:00:00.000Z", endDate: "2019-12-23T23:59:59.000Z" } }], 
        
      }
     
    ],
    constants:{
      id: "constants"
      defaultFuelPrice: 400,
      defaultDieselPrice:650,
      refuelThreshold:400,
      criticalRefuelThreshold:300
      operationalOverhead:15
    }
    
  )
}

query detailedSchedules {
  detailedSchedules(
    date:"2019-12-16T00:00:00.000Z",
    vessels: [
    {
      id: "9688348"
          vessel: {
              id: "9688348"
              name: "NCC Wafa"
            	isParked:false
              clean: "c"
              details: {
                  id: "12345"
                  charteringCost: 12000
                  contractExpiration: "2021-11-16"
                  sizeCategory: "LR"
                  cleaningTimeMultiplier: 1
              }
              portRestrictions: []
            	bunkerRequirements:  {
                id: "9688348"
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
             currentStatus: {
                id: "9688348"
                availableFrom: "2019-12-16T00:00:00.000Z"
                lastKnownPort: "SAYNB" 
                lastProduct: ""
                startingFuel: 616.0
        		}

      }
      q88Vessel: {
        id: "9688348"
        name: "NCC Wafa"
        totalProductCapacity: 52434.21
        scnt:27515.13,
        imoClass:"2,3"
        cleaningTimeMultiplier:0.1
        cargoPumpingRateM3PerS:2700        
        dimensions:{
          id:"9688348"
          beam: 32.24
          overallLength: 183.12
          aftParallelBodyDistance: 55.899
          forwardParallelBodyDistance: 34.32
        }
        speedCapabilities: {
          id: "9688348"
          ladenEconomicSpeed: 12.5
          ladenMaxSpeed: 14
          ballastEconomicSpeed: 13
          ballastMaxSpeed: 14

        }
        carryingCapacity:{
          id: "9688348"
          fuelCapacity: 1173.5
          diesalCapacity: 490.8
          gasOilCapacity: 36
        }
      }
    requirements: ["CIT20191250"]
    }]
    requirements: [
      {id: "CIT20191250", cleanStatus: "c", 
        longs: [{id:"CIT20191250", product: "dyed gas oil", quantity: 47696.1885, location: "SAYNB", valid: {id:"CIT20191250", startDate: "2019-12-18T00:00:00.000Z", endDate: "2019-12-19T23:59:59.000Z" } }] 
        shorts: [{id: "CIT20191250", product: "dyed gas oil", quantity: 47696.1885, location: "SAJED", valid: {id: "CIT20191250",startDate: "2019-12-22T00:00:00.000Z", endDate: "2019-12-23T23:59:59.000Z" } }],   
      }
    ]
		
  ){
    vessel
    requirements{
      id
      cost
      actions{
        id
        type
      }
    }
    
  }
}