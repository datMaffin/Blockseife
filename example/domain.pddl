(define (domain lighting)
  (:requirements :strips)
  (:predicates
    (light_on ?x)
    (sun_light ?x)
  )
  (:action switch_light_on
    :parameters(?x)
    :precondition (not (light_on ?x))
    :effect (light_on ?x ) 
  )
  (:action switch_light_off
    :parameters(?x)
    :precondition (light_on ?x)
    :effect (not (light_on ?x ))
  )
)
