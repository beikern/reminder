package com.valsea.reminder.projections.reminder.quartz

import akka.actor.typed.ActorSystem
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import com.valsea.reminder.DBConnectionProvider
import com.valsea.reminder.aggregates.service.ServiceAggregate
import com.valsea.reminder.projections.QuillJDBCSession
import com.valsea.reminder.quartz.QuartzRuntimeDependencies
import io.getquill.{MysqlJdbcContext, SnakeCase}

object ReminderQuartzProjection {
  def init(
      system: ActorSystem[_],
      quillCtx: MysqlJdbcContext[SnakeCase],
      connectionProvider: DBConnectionProvider,
      quartzRuntimeDependencies: QuartzRuntimeDependencies
  ): Unit = {
    ClusterSingleton(system).init(
      SingletonActor(
        ProjectionBehavior(
          createProjectionFor(system, quillCtx, connectionProvider, quartzRuntimeDependencies)
        ),
        "quartz-singleton-projection-actor"
      )
        .withStopMessage(ProjectionBehavior.Stop)
    )

    ()
  }

  private def createProjectionFor(
      system: ActorSystem[_],
      quillCtx: MysqlJdbcContext[SnakeCase],
      connectionProvider: DBConnectionProvider,
      quartzRuntimeDependencies: QuartzRuntimeDependencies
  ): ExactlyOnceProjection[Offset, EventEnvelope[ServiceAggregate.Event]] = {
    val tag = ServiceAggregate.allTag

    val sourceProvider: SourceProvider[Offset, EventEnvelope[ServiceAggregate.Event]] =
      EventSourcedProvider.eventsByTag[ServiceAggregate.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag
      )

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("reminderQuartzProjection", tag),
      sourceProvider,
      handler = () => new ReminderQuartzProjectionHandler(connectionProvider, quartzRuntimeDependencies),
      sessionFactory = () => new QuillJDBCSession(quillCtx)
    )(system)
  }
}
