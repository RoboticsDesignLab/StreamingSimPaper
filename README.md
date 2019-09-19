# AirSim with Akka Streams
This app was used to generate the test data for __Use of AKKA Streams to Inform Forward Simulation in Robot Control__

There are 5 models that use the same algorithm to simulate evader/pursuer chase.
The algorithm is very simple - because the evader is slower but can make agile turns,
whenever the pursuer is close it will start a turn. The pursuer follows, but because it has larger turning radius, it will take longer to turn.
As such, under ideal situation, the drones will end up orbiting. Each models provides a different level of parallelization.

### Model01 - Sequential model with simulator paused between turns
This represents the ground truth as the paused simulator serves as a synchronization layer that shields the algorithm from both stale data and delays in actions. The simulator starts paused. The measurements are taken, and the actions calculated and sent to the agents. After that, the simulator is run for 100ms. Then the process repeats. Because the communication with the simulator happens in the paused state it can be done in a blocking manner and the calculations start only once all the requests have finished.

### Model02 - Sequential model with simulator running
This is the same as the previous model, but without the paused simulator acting as the synchronization layer. Because all commands run in sequence, the next request can only be sent once the previous one completes and the calculations can only start once all the requests finished. This creates a varying level of staleness among the received data that enter the calculations. It also increases the time lag between actions being sent to the agents. With the pursuer moving at 10m/s, even relatively small delay of 500ms translates to a location difference of 5m.

### Model03 - Partially parallelized model
This model represents a simple (if short-sighted) solution to the concurrency issue. Since each agent is treated as a separate entity, at each timestamp, they are wrapped in their individual future. This means that while all communication and calculations for a single agent runs sequentially, the code blocks for both agents are run in parallel.

### Model04 - Fully parallelized model using Akka Streams
In this model, not only the agents move in parallel, but each operation within each agent is parallelized as well. There are no inter-process dependencies: the location is updated independently, the calculations use whatever latest data available and once the new action is calculated, it is sent to the agent. The whole process moves in the interleaved manner. 

### Model05 - Fully parallelized model with Kafka
This model builds on the previous one and adds on a message broker to allow for out of process and out of machine communication. Instead of running the calculation, this model persists the location readings to Kafka.
They are picked up by KafkaSolver, which runs the calculations and pushes the resulting actions back to Kafka.
The actions are picked up by the Model05 and pushed to AirSim.
This requires Kafka running somewhere (preferably on another machine) and KafkaSolver running yet on another machine.

## Config
#### AirSim
You will need AirSim running somewhere, preferably on another network - the whole experiment is to show effects of latency when using a cloud GPU.
The config file for AirSim is under src/main/resources/settings.json. The IP of the AirSim needs to be set under Constants.

#### Postgresql
The steering decisions are persisted in Postgresql. Run all the migrations under src/main/resources/sql/:

    psql your_db < src/main/resources/sql/* 
    
and set the connection details in the application.conf file.

## Running
    
    sbt run
    # then select the Model to run  

You can also run the models directly, e.g.:

    sbt "runMain net.nextlogic.airsim.paper.solvers.KafkaSolver"


 
