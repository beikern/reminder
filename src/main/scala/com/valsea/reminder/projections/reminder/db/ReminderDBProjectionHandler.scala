package com.valsea.reminder.projections.reminder.db

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import com.valsea.reminder.aggregates.service.ServiceAggregate
import com.valsea.reminder.projections.QuillJDBCSession
import com.valsea.reminder.repositories.ReminderRepository
import com.valsea.reminder.syntaxImplicits._

class ReminderDBProjectionHandler(
    repo: ReminderRepository
) extends JdbcHandler[EventEnvelope[ServiceAggregate.Event], QuillJDBCSession]() {
  override def process(
      session: QuillJDBCSession,
      envelope: EventEnvelope[ServiceAggregate.Event]
  ): Unit =
    envelope.event match {
      case ServiceAggregate.ServiceRegistered(_, _) => ()
      case ServiceAggregate.ServiceUpdated(_, _) => ()
      case ServiceAggregate.ReminderAdded(serviceId, _, reminder) =>
        repo.insertReminder(sqlCtx = session.quillCtx, reminder.toDBModel(serviceId))
      case ServiceAggregate.ReminderStatusUpdated(serviceId, reminderId, status) =>
        repo.updateStatus(sqlCtx = session.quillCtx, serviceId, reminderId, status.toString)
    }
}
