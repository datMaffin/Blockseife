# Example
In this folder are files and scripts for a small example of a running system.

## Description
This example is demonstrating the automatic planning and execution.

## Howto
1. Get a executable for the Fast Forward solver `ff` and put it into an folder 
   with the name `FF`
2. Put the `ff_rest_wrapper.py` script into the same location as the `FF` *folder* 
3. Start the `ff_rest_wrapper.py` script and the `collector_and_execution.py` 
   script (the only python dependency is `flask`)
4. Start the `Blockseife` project with `sbt run`
5. Observe how the project interacts with the Solver service and executes the 
   result.

## Run your own example
1. Need the domain and the goal; the goal is a pddl problem without the 
   `(:init...)` block.
2. Change the domain and the goal definition in the `Main.scala` app class.
3. Create a *collector_and_execution* service that has the corresponding 
   predicates and actions.
    - If you created a different collector or execution you may need to update 
      the adresses in `Main.scala`
    - For interfaces see (TODO)

## Exchange the solver
1. Choose which REST interace to use (TODO) 
2. Choose how to return the resulting steps:
    - Create a `converter.to.step` to parse the output of the other solver into 
      the internally used step class instances.
    - Return a correct json array with the objects of the internal step class 
      and use the `alreadyJsonStepsConverter` from `converter.to.step` in the 
      `Pipeline.Settings` creation (instead of the `rawFFOutputStepsConverter`.
3. Check that the `Main.scala` app object uses the correct Solver actor 
   (depending on the interface) and talks to the right adress.
