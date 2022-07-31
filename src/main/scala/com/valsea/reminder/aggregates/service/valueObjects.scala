package com.valsea.reminder.aggregates.service

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{ JsonSubTypes, JsonTypeInfo }

object valueObjects {
  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
  )
  @JsonSubTypes(
    Array(
      new Type(value = classOf[CronTime], name = "cronTime"),
      new Type(value = classOf[OnlyOnce], name = "onlyOnce")
    )
  )
  sealed trait ScheduledTime
  case class CronTime(cronExpression: String) extends ScheduledTime
  case class OnlyOnce(when: Long)             extends ScheduledTime


  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
  )
  @JsonSubTypes(
    Array(
      new Type(value = classOf[Scheduled.type], name = "scheduled"),
      new Type(value = classOf[Delivered.type], name = "delivered"),
      new Type(value = classOf[Undelivered.type], name = "undelivered")
    )
  )
  sealed trait ReminderStatus
  case object Scheduled extends ReminderStatus {
    override def toString: String = "SCHEDULED"
  }
  case object Delivered extends ReminderStatus {
    override def toString: String = "DELIVERED"
  }
  case object Undelivered extends ReminderStatus {
    override def toString: String = "UNDELIVERED"
  }
}
