(define (problem sorted_tower)
  (:domain the_three_towers)
  (:objects a b c d e f g h)
  (:goal (and (on_tower1 a b) 
              (on_tower1 b c)
              (on_tower1 c d)
              (on_tower1 e f)
              (on_tower1 g h)
              (hand_empty)
              (on_top a))
)
