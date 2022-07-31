package com.valsea.reminder.projections.reminder.db

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ ExactlyOnceProjection, SourceProvider }
import akka.projection.{ ProjectionBehavior, ProjectionId }
import com.valsea.reminder.aggregates.service.ServiceAggregate
import com.valsea.reminder.projections.QuillJDBCSession
import com.valsea.reminder.repositories.ReminderRepository
import io.getquill.{ MysqlJdbcContext, SnakeCase }

object ReminderDBProjection {
  def init(
      system: ActorSystem[_],
      quillCtx: MysqlJdbcContext[SnakeCase],
      reminderRepository: ReminderRepository
  ): Unit =
    ShardedDaemonProcess(system).init(
      name = "reminderJdbcProjection",
      ServiceAggregate.tags.size,
      index => ProjectionBehavior(createProjectionFor(system, quillCtx, reminderRepository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )

  private def createProjectionFor(
      system: ActorSystem[_],
      quillCtx: MysqlJdbcContext[SnakeCase],
      repository: ReminderRepository,
      index: Int
  ): ExactlyOnceProjection[Offset, EventEnvelope[ServiceAggregate.Event]] = {
    val tag = ServiceAggregate.tags(index)

    val sourceProvider: SourceProvider[Offset, EventEnvelope[ServiceAggregate.Event]] =
      EventSourcedProvider.eventsByTag[ServiceAggregate.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag
      )

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("reminderJdbcProjection", tag),
      sourceProvider,
      handler = () => new ReminderDBProjectionHandler(repository),
      sessionFactory = () => new QuillJDBCSession(quillCtx)
    )(system)
  }
}
