package com.valsea.reminder.repositories.model

case class Reminder(
    serviceId: String,
    reminderId: String,
    status: String,
    cronTime: Option[String],
    onlyOnce: Option[Long],
    data: Option[Array[Byte]]
)
