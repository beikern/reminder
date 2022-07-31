package com.valsea.reminder.aggregates.service

import com.valsea.reminder.aggregates.service.entities.{ ReminderId, ServiceId }

object errors {
  case class ServiceIsNotRegisteredError(serviceId: ServiceId) extends Exception {
    override def getMessage: String = "Operation forbidden. Service is not registered."
  }
  case class RemindersAlreadyRegisteredError(serviceId: ServiceId, registeredIds: List[ReminderId]) extends Exception {
    override def getMessage: String = {
      s"The reminder ids ${registeredIds.mkString(",")} already exists in service with id $serviceId. None of the requested reminders will be registered."
    }
  }
}
