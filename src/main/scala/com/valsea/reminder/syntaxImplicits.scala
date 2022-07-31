package com.valsea.reminder
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import com.valsea.reminder.ScheduledTimeMessage.SealedValue
import com.valsea.reminder.aggregates.service.entities.{ Reminder, Service, ServiceId }
import com.valsea.reminder.{ Service => ProtoService }
import com.valsea.reminder.aggregates.service.valueObjects
import com.valsea.reminder.grpc.MandatoryDataNotAvailableError
import com.valsea.reminder.repositories.model.{ Reminder => DBReminder }
import com.valsea.reminder.{ ReminderStatus => ProtoReminderStatus }
import com.valsea.reminder.aggregates.service.valueObjects.ReminderStatus
import com.valsea.reminder.{ Reminder => ProtoReminder }

import java.time.Instant
object syntaxImplicits {
  implicit class ProtoServiceExtendedSyntax(protoService: ProtoService) {
    def toDomain: Service = {
      Service(endpoint = protoService.endpoint, patienceInMins = protoService.patienceInMins)
    }
  }

  implicit class ProtoReminderRequestExtendedSyntax(protoReminder: CreateReminderRequest) {
    def toDomain: Reminder = {
      val scheduledTime: valueObjects.ScheduledTime = protoReminder.scheduledTime.asMessage.sealedValue match {
        case SealedValue.Empty       => throw new MandatoryDataNotAvailableError
        case SealedValue.Cron(value) => valueObjects.CronTime(value.expression)
        case SealedValue.Once(value) =>
          valueObjects.OnlyOnce(value.when.getOrElse(throw new MandatoryDataNotAvailableError).seconds)
      }
      Reminder(id = protoReminder.reminderId, scheduledTime = scheduledTime, protoReminder.data.map(_.toByteArray))
    }

    def dataExceedsLimit: Boolean = {
      protoReminder.data.fold(false)(bs => bs.size() / 1024 > 64)
    }
  }

  implicit class DomainReminderSyntax(domainReminder: Reminder) {
    def toDBModel(serviceId: ServiceId): DBReminder = {
      domainReminder.scheduledTime match {
        case valueObjects.CronTime(cronExpression) =>
          DBReminder(
            serviceId = serviceId,
            reminderId = domainReminder.id,
            status = valueObjects.Scheduled.toString,
            cronTime = Some(cronExpression),
            onlyOnce = None,
            data = domainReminder.data
          )
        case valueObjects.OnlyOnce(when) =>
          DBReminder(
            serviceId = serviceId,
            reminderId = domainReminder.id,
            status = valueObjects.Scheduled.toString,
            cronTime = None,
            onlyOnce = Some(when),
            data = domainReminder.data
          )
      }
    }
  }

  implicit class ProtoReminderStatusSyntax(protoReminderStatus: ProtoReminderStatus) {
    def toDomain: ReminderStatus = {
      protoReminderStatus match {
        case ReminderStatus.SCHEDULED   => valueObjects.Scheduled
        case ReminderStatus.DELIVERED   => valueObjects.Delivered
        case ReminderStatus.UNDELIVERED => valueObjects.Undelivered
        case _                          => throw new RuntimeException("Unexpected value")
      }
    }
  }

  implicit class DBReminderSyntax(dBReminder: DBReminder) {
    def toProto: ProtoReminder = {

      val protoStatus: ProtoReminderStatus = dBReminder.status match {
        case "SCHEDULED"   => ReminderStatus.SCHEDULED
        case "DELIVERED"   => ReminderStatus.DELIVERED
        case "UNDELIVERED" => ReminderStatus.UNDELIVERED
      }

      val cronTime = dBReminder.cronTime.map(CronTime(_))
      val onlyOnce = dBReminder.onlyOnce.map(long => OnlyOnce(Some(Timestamp(Instant.ofEpochMilli(long)))))

      val scheduledTime: ScheduledTime = (cronTime, onlyOnce) match {
        case (Some(cronTime), None) => cronTime
        case (None, Some(onlyOnce)) => onlyOnce
        case _                      => throw new RuntimeException("Unexpected value")
      }

      ProtoReminder(
        reminderId = dBReminder.reminderId,
        scheduledTime = scheduledTime,
        status = protoStatus,
        data = dBReminder.data.map(ByteString.copyFrom)
      )
    }
  }

  implicit class StringSyntax(string: String) {

    /** GRPC over HTTP2 does not permit all the ASCII charset in HEADER names, as stated here
      * https://chromium.googlesource.com/external/github.com/grpc/grpc/+/HEAD/doc/PROTOCOL-HTTP2.md header keys must
      * enforce the following regex: 1*( %x30-39 / %x61-7A / “_” / “-” / “.”) ; 0-9 a-z _ - . For now we will only
      * substitute the forbidden characters in a typical URL: ": and /"
      */
    def normalizeGrpcHeaderKey = {
      string.replaceAll("[:/]", "-")
    }
  }

}
