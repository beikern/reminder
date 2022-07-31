package com.valsea.reminder

import akka.actor.typed.ActorSystem
import com.valsea.reminder.aggregates.service.ServiceAggregate

package object aggregates {
  def initializeAggregates(system: ActorSystem[_]) = {
    ServiceAggregate.init(system)
  }
}
