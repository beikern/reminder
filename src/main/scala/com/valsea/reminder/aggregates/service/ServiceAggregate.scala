package com.valsea.reminder.aggregates.service

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria }
import com.valsea.reminder.aggregates.CborSerializable
import com.valsea.reminder.aggregates.service.entities._
import com.valsea.reminder.aggregates.service.errors._
import com.valsea.reminder.aggregates.service.state._
import com.valsea.reminder.aggregates.service.valueObjects._

import scala.annotation.unused
import scala.concurrent.duration._
object ServiceAggregate {

  sealed trait Command extends CborSerializable

  final case class RegisterService(service: Service, replyTo: ActorRef[StatusReply[String]]) extends Command
  final case class AddReminders(reminders: List[Reminder], replyTo: ActorRef[StatusReply[ReminderSummary]])
      extends Command
  final case class UpdateReminderStatus(
      reminderId: ReminderId,
      status: ReminderStatus,
      replyTo: ActorRef[StatusReply[String]]
  ) extends Command

  sealed trait Event extends CborSerializable {
    def serviceId: ServiceId
  }
  final case class ServiceRegistered(serviceId: ServiceId, service: Service)                 extends Event
  final case class ServiceUpdated(serviceId: ServiceId, service: Service)                 extends Event
  final case class ReminderAdded(serviceId: ServiceId, service: Service, reminder: Reminder) extends Event
  final case class ReminderStatusUpdated(serviceId: ServiceId, reminderId: ReminderId, status: ReminderStatus)
      extends Event

  final case class ReminderSummary(summary: List[String]) extends CborSerializable

  private def serviceNotRegisteredCommandHandler(
      serviceId: ServiceId,
      @unused state: State,
      command: Command
  ): ReplyEffect[Event, State] = {
    command match {
      case RegisterService(service, replyTo) =>
        Effect
          .persist(ServiceRegistered(serviceId, service))
          .thenReply(replyTo)(_ => StatusReply.success(s"Service $serviceId has been registered"))
      case AddReminders(_, replyTo) =>
        Effect.reply(replyTo)(
          StatusReply.Error(ServiceIsNotRegisteredError(serviceId))
        )
      case UpdateReminderStatus(_, _, replyTo) =>
        Effect.reply(replyTo)(
          StatusReply.Error(ServiceIsNotRegisteredError(serviceId))
        )
    }
  }

  private def serviceRegisteredCommandHandler(
      serviceId: ServiceId,
      state: State,
      command: Command
  ): ReplyEffect[Event, State] = {
    command match {
      case RegisterService(service, replyTo) =>
        Effect
          .persist(ServiceUpdated(serviceId, service))
          .thenReply(replyTo)(_ => StatusReply.success(s"Service $serviceId has been registered"))
      case AddReminders(reminders, replyTo) =>
        if (!state.isRegistered) {
          Effect.reply(replyTo)(
            StatusReply.Error(ServiceIsNotRegisteredError(serviceId))
          )
        } else {
          val remindersAlreadyRegistered = reminders.collect {
            case Reminder(id, _, _) if state.hasReminder(id) => id
          }
          if (remindersAlreadyRegistered.nonEmpty) {
            Effect.reply(replyTo)(
              StatusReply.Error(RemindersAlreadyRegisteredError(serviceId, remindersAlreadyRegistered))
            )
          } else {
            val eventsToPersist = reminders.map(reminder => ReminderAdded(serviceId, state.service, reminder))

            Effect
              .persist(eventsToPersist)
              .thenReply(replyTo)(_ =>
                StatusReply.Success(
                  ReminderSummary(
                    reminders.map(reminder => s"Reminder ${reminder.id} in service ${serviceId} has been registered")
                  )
                )
              )
          }
        }
      case UpdateReminderStatus(reminderId, status, replyTo) =>
        Effect
          .persist(ReminderStatusUpdated(serviceId, reminderId, status))
          .thenReply(replyTo)(_ =>
            StatusReply.success(s"Reminder $reminderId status has been updated with value $status")
          )
    }
  }

  private def commandHandler(
      serviceId: ServiceId,
      state: State,
      command: Command
  ): ReplyEffect[Event, State] =
    if (state.isRegistered) {
      serviceRegisteredCommandHandler(serviceId, state, command)
    } else {
      serviceNotRegisteredCommandHandler(serviceId, state, command)
    }

  private def handleEvent(state: State, event: Event): State =
    event match {
      case ServiceRegistered(_, service) =>
        state.registerService(service)
      case ServiceUpdated(_, service) =>
        state.updateService(service)
          .updateService(service)
      case ReminderAdded(_, _, reminder) =>
        state.addReminder(reminder.id)
      case ReminderStatusUpdated(_, _, _) =>
        state
    }

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("serviceAggregate")

  val tags   = Vector.tabulate(5)(i => s"serviceAggregate-$i")
  val allTag = "serviceAggregate-all"

  def init(system: ActorSystem[_]): Unit = {
    system.log.info("Initializing Service Aggregate")

    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      val i           = math.abs(entityContext.entityId.hashCode % tags.size)
      val selectedTag = tags(i)
      ServiceAggregate(entityContext.entityId, selectedTag)
    })

    ()
  }

  def apply(serviceId: ServiceId, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, serviceId),
        emptyState = State.empty,
        commandHandler = (state, command) => commandHandler(serviceId, state, command),
        eventHandler = (state, event) => handleEvent(state, event)
      )
      .withTagger(_ => Set(projectionTag, allTag))
      .withRetention(
        RetentionCriteria
          .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3)
      )
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
  }
}
