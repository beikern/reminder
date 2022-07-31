package com.valsea.reminder.quartz

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.valsea.reminder.aggregates.service.ServiceAggregate
import com.valsea.reminder.aggregates.service.entities.{Reminder, Service, ServiceId}
import com.valsea.reminder.aggregates.service.valueObjects.{Delivered, ReminderStatus, Undelivered}
import com.valsea.reminder.http.ReminderHttpService
import com.valsea.reminder.http.reminder.httpErrors.HttpErrors
import com.valsea.reminder.http.reminder.requests.ReminderRequest
import org.quartz.{Job, JobExecutionContext, JobExecutionException}
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class ReminderJob extends Job {

  private val logger   = LoggerFactory.getLogger(getClass)

  // Dependencies
  private var reminderHttpService: ReminderHttpService = _
  private var sharding: ClusterSharding                = _
  private implicit var blockingEc: ExecutionContext            = _  // Be aware that this ec will block like crazy because of the synchronous nature of quartz. We will mitigate that problem injecting a blocking execution context from outside.

  // Job parameters
  private var reminder: Reminder = _
  private var serviceId: String  = _
  private var service: Service   = _
  private implicit var askTimeout: Timeout = _

  // Used by quartz to fill the variables using the JobDataMap
  def setReminder(reminder: Reminder): Unit = this.reminder = reminder
  def setService(service: Service): Unit    = this.service = service
  def setServiceId(id: String): Unit        = this.serviceId = id
  def setAggregateAskTimeout(timeout: Timeout): Unit = this.askTimeout = timeout

  def setDependencies(quartzRuntimeDependencies: QuartzRuntimeDependencies): Unit = {
    this.reminderHttpService = quartzRuntimeDependencies.reminderHttpService
    this.sharding = quartzRuntimeDependencies.sharding
    this.askTimeout = quartzRuntimeDependencies.askTimeout
    this.blockingEc = quartzRuntimeDependencies.blockingEc
  }

  override def execute(context: JobExecutionContext): Unit = {
    val reminderRequest =
      ReminderRequest(reminder_id = reminder.id, sent_at = Instant.now().toEpochMilli, data = reminder.data)

    val result = (for {
      _ <- reminderHttpService.sendReminder(service, reminderRequest)
      _ <- updateReminderCommandToServiceAggregate(serviceId, reminder, Delivered)
    } yield {
      logger.info(
        s"Job execution for serviceId $serviceId, service: $service, reminder: $reminder finished successfully. Delivered."
      )
    }).recoverWith { case ex: HttpErrors =>
      logger.info(
        s"Job execution for serviceId $serviceId, service: $service, reminder: $reminder finished with error. Undelivered. Reason: ${ex.getMessage}"
      )
      updateReminderCommandToServiceAggregate(serviceId, reminder, Undelivered).map(_ => ())
    }

    Try {
      Await.result(result, (service.patienceInMins + 1).minutes) // We give here a minute of leeway to the await.
    }.recover { case ex =>
      throw new JobExecutionException(ex)
    }.get

    ()
  }

  private def updateReminderCommandToServiceAggregate(
      serviceId: ServiceId,
      reminder: Reminder,
      status: ReminderStatus
  ): Future[String] = {
    val entityRef = sharding.entityRefFor(ServiceAggregate.EntityKey, serviceId)
    entityRef
      .askWithStatus(
        ServiceAggregate.UpdateReminderStatus(reminderId = reminder.id, status = status, _)
      )
  }
}
