package com.valsea.reminder.aggregates.service
import com.valsea.reminder.aggregates.service.valueObjects.ScheduledTime

object entities {
  type ReminderId = String
  type ServiceId  = String

  case class Service(endpoint: String, patienceInMins: Int)
  case class Reminder(id: ReminderId, scheduledTime: ScheduledTime, data: Option[Array[Byte]])
}
