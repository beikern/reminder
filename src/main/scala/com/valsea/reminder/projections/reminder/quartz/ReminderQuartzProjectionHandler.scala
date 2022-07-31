package com.valsea.reminder.projections.reminder.quartz

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import com.valsea.reminder.DBConnectionProvider
import com.valsea.reminder.aggregates.service.{ServiceAggregate, valueObjects}
import com.valsea.reminder.projections.QuillJDBCSession
import com.valsea.reminder.quartz.{QuartzRuntimeDependencies, QuartzScheduler, ReminderJob, ReminderJobFactory}
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.JobBuilder._
import org.quartz.Scheduler
import org.quartz.TriggerBuilder._

class ReminderQuartzProjectionHandler(
    connectionProvider: DBConnectionProvider,
    quartzRuntimeDependencies: QuartzRuntimeDependencies
) extends JdbcHandler[EventEnvelope[ServiceAggregate.Event], QuillJDBCSession]() {
  var scheduler: Scheduler = _

  override def start(): Unit = {
    scheduler =
      QuartzScheduler
        .createScheduler(connectionProvider)
        .get // in case the scheduler couldn't be created the projection will restart.
    scheduler.setJobFactory(new ReminderJobFactory(quartzRuntimeDependencies))
  }

  override def stop(): Unit = {
    QuartzScheduler.shutdownScheduler(scheduler)
    ()
  }

  override def process(
      session: QuillJDBCSession,
      envelope: EventEnvelope[ServiceAggregate.Event]
  ): Unit =
    envelope.event match {
      case ServiceAggregate.ServiceRegistered(_, _) => ()
      case ServiceAggregate.ServiceUpdated(_, _) => ()
      case ServiceAggregate.ReminderStatusUpdated(_, _, _) => ()
      case ServiceAggregate.ReminderAdded(serviceId, service, reminder) =>
        val jobIdentity = s"$serviceId-${reminder.id}"

        val job = newJob(classOf[ReminderJob])
          .withIdentity(jobIdentity)
          .build()

        job.getJobDataMap.put("service", service)
        job.getJobDataMap.put("serviceId", serviceId)
        job.getJobDataMap.put("reminder", reminder)

        val trigger = reminder.scheduledTime match {
          case valueObjects.CronTime(cronExpression) =>
            newTrigger()
              .withIdentity(s"$jobIdentity-cron-trigger")
              .withSchedule(cronSchedule(cronExpression))
              .forJob(jobIdentity)
              .build()
          case valueObjects.OnlyOnce(when) =>
            newTrigger()
            .withIdentity(s"$jobIdentity-simple-trigger")
            .startAt(java.util.Date.from(java.time.Instant.ofEpochSecond(when)))
            .forJob(jobIdentity)
            .build()
        }
        scheduler.scheduleJob(job, trigger)
        ()
    }
}
