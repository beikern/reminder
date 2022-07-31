package com.valsea.reminder.grpc

import akka.NotUsed
import akka.actor.typed.{ ActorSystem, DispatcherSelector }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.MetadataBuilder
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.Timeout
import com.valsea.reminder._
import com.valsea.reminder.aggregates.service.entities.ServiceId
import com.valsea.reminder.aggregates.service.errors.{ RemindersAlreadyRegisteredError, ServiceIsNotRegisteredError }
import com.valsea.reminder.aggregates.service.{ ServiceAggregate, entities }
import com.valsea.reminder.http.ReminderHttpService
import com.valsea.reminder.http.reminder.httpErrors
import com.valsea.reminder.repositories.ReminderRepository
import com.valsea.reminder.syntaxImplicits._
import io.getquill.{ MysqlJdbcContext, SnakeCase }
import io.grpc.Status
import org.slf4j.LoggerFactory
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
class ReminderServiceGrpc(
    system: ActorSystem[_],
    reminderHttpService: ReminderHttpService,
    sqlCtx: MysqlJdbcContext[SnakeCase],
    reminderRepository: ReminderRepository
) extends ReminderService {
  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector
        .fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )
  implicit val timeout       = Timeout(20.seconds)

  private val logger                    = LoggerFactory.getLogger(getClass)
  private val sharding: ClusterSharding = ClusterSharding(system)

  import system.executionContext
  implicit val classicSystem = system.classicSystem


  override def register(request: RegisterRequest): Future[RegisterResponse] = {
    logger.info(s"Received register request: $request")

    request.service match {
      case Some(service) =>
        (for {
          _                        <- reminderHttpService.checkValidity(service.toDomain)
          serviceAggregateResponse <- sendRegisterCommandToServiceAggregate(service.serviceId, service.toDomain)
        } yield {
          serviceAggregateResponse
        }).recoverWith {
          case httpErrors.InvalidServiceEndpointError(invalidEndpoint, error) =>
            val metadata = new MetadataBuilder()
              .addText(s"$invalidEndpoint", error)
              .build()

            Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT, metadata))
          case httpErrors.ReminderRequestFailedError(_, service, error) =>
            val metadata = new MetadataBuilder()
              .addText(service.normalizeGrpcHeaderKey, error)
              .build()
            Future.failed(new GrpcServiceException(Status.FAILED_PRECONDITION, metadata))

        }

      case None =>
        val metadata = new MetadataBuilder()
          .addText(s"service", "Service couldn't be empty.")
          .build()

        Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT, metadata))
    }
  }

  override def createReminders(request: CreateRemindersRequest): Future[CreateRemindersResponse] = {
    logger.info(s"Received create reminders request: $request")

    val requestExceedsSizeLimit = request.reminders.filter(_.dataExceedsLimit)

    if (requestExceedsSizeLimit.nonEmpty) {
      val metadataBuilder = new MetadataBuilder()
      requestExceedsSizeLimit.foreach(request =>
        metadataBuilder.addText(s"${request.serviceId}-${request.reminderId}", "exceeds 64KB data limit")
      )
      val metadata = metadataBuilder.build()

      Future.failed(new GrpcServiceException(Status.FAILED_PRECONDITION, metadata))
    } else {
      val remindersByServiceId = request.reminders
        .groupBy(_.serviceId)
        .view
        .mapValues { request =>
          request.map(_.toDomain).toList
        }
        .toMap

      val responses = remindersByServiceId.map { case (serviceId, reminders) =>
        sendAddRemindersCommandToServiceAggregate(serviceId, reminders)
      }

      Source(responses)
        .mapAsync(1)(identity)
        .toMat(Sink.collection[CreateRemindersResponse, List[CreateRemindersResponse]])(Keep.right)
        .run() // Another alternative is Future.sequence but with big collections its problematic.
        .map { responses =>
          responses.foldRight(CreateRemindersResponse(messages = List.empty))((response, b) =>
            CreateRemindersResponse(messages = b.messages.toList ::: response.messages.toList)
          )
        }
        .recoverWith {
          case RemindersAlreadyRegisteredError(serviceId, registeredIds) =>
            val metadataBuilder = new MetadataBuilder()
            registeredIds.foreach(reminderId => metadataBuilder.addText(s"$serviceId-$reminderId", "already exists"))
            val metadata = metadataBuilder.build()
            Future.failed(new GrpcServiceException(Status.ALREADY_EXISTS, metadata))
          case ServiceIsNotRegisteredError(serviceId) =>
            val metadata = new MetadataBuilder().addText(s"$serviceId", "Target service is not registered").build()
            Future.failed(new GrpcServiceException(Status.FAILED_PRECONDITION, metadata))
        }
    }
  }

  override def listReminders(request: ListRemindersRequest): Source[Reminder, NotUsed] = {
    logger.info(s"Received list reminders request: $request")

    val result: Future[Source[Reminder, NotUsed]] = Future {
      reminderRepository.getReminder(
        sqlCtx,
        request.reminderIds.toList,
        request.serviceIds.toList,
        request.status.map(_.toDomain)
      )
    }(blockingJdbcExecutor).map { repoResponse =>
      Source(repoResponse.map(_.toProto))
    }

    Source.futureSource(result).mapMaterializedValue(_ => NotUsed)
  }

  private def sendAddRemindersCommandToServiceAggregate(
      serviceId: ServiceId,
      reminders: List[entities.Reminder]
  ): Future[CreateRemindersResponse] = {
    val entityRef = sharding.entityRefFor(ServiceAggregate.EntityKey, serviceId)
    entityRef
      .askWithStatus(
        ServiceAggregate.AddReminders(reminders, _)
      )
      .map(response => CreateRemindersResponse(response.summary))
  }

  private def sendRegisterCommandToServiceAggregate(
      serviceId: ServiceId,
      service: entities.Service
  ): Future[RegisterResponse] = {
    val entityRef = sharding.entityRefFor(ServiceAggregate.EntityKey, serviceId)
    entityRef
      .askWithStatus(
        ServiceAggregate.RegisterService(service, _)
      )
      .map(response => RegisterResponse(response))
  }
}
