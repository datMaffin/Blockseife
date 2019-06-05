# Blockseife
Modular Service Oriented Architecture for Planning

## What is/was this project about?
This is a project created as part of a student project at the University of 
Stuttgart at the IAAS.

The goal was to create an architecture for planning. The proposed software 
stack for the prototype was the Akka toolkit in combination with Scala. We 
decided to stick with the proposed software stack.

# TODO: The following is a rough outline for future additions of this `README`

## The Prototype
### Capabilities
- The Akka actor, streams and http stuff
    * Actors/Service Oriented -> Really modular
    * Streams
    * Tests
- The Planning stuff
    * Is able to create a Pipeline, which takes a goal and a domain and execute the steps in the right order while waiting for precondition to turn true.
    * Describe Pipeline in a lot of detail (Also describe interfaces) (Ascii art???)

### Limitations
- Error handling not really developed
    * Errors in the actors are not really handled (Pretty much every place where error handling can be implemented is annotated with a TODO...)
    * Create custom exceptions and choose how to handle them by using the Akka supervisor feature
- "Intelligent" error handling is missing
    * I.e. Automatic replanning when a failure in the Scheduler occurs
- Missing an entity, that is able to manage Pipelines and communicate with the outside.
- Currently only provided support for fast forward solver
- The Execution Service has to be idempotent (message only guarantees at least once)
- The pddl parser and precondition checker only supports PDDL 1.2 with the `:strips` requirement. In addition the order of definitions is not allowed to be in a random order. (See Scala-doc of the implementation)
    * The case of everything in the domain *should* be ignored. However, it would be a good idea to refactor the current implementation.
- Implement the interaction with external services over https and other security related features.
