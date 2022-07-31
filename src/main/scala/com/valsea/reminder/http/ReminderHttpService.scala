package com.valsea.reminder.http

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity, Uri }
import akka.stream.scaladsl.{ Flow, Keep, RestartSource, Sink, Source }
import akka.stream.{ KillSwitches, RestartSettings }
import com.valsea.reminder.aggregates.service.entities.Service
import com.valsea.reminder.http.reminder.httpErrors._
import com.valsea.reminder.http.reminder.requests._
import com.valsea.reminder.http.reminder.responses.ReminderResponse
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait ReminderHttpService {
  def sendReminder(service: Service, request: ReminderRequest, withBackoff: Boolean = true): Future[ReminderResponse]
  def checkValidity(service: Service): Future[ReminderResponse] = {
    val fakeRequest = ReminderRequest(service.endpoint, Instant.now().toEpochMilli, None)
    sendReminder(service, fakeRequest, withBackoff = false)
  }
}

class ReminderHttpServiceImpl(system: ActorSystem[_]) extends ReminderHttpService with JacksonSupport {

  implicit val classicSystem = system.classicSystem
  import classicSystem.dispatcher
  private val logger = LoggerFactory.getLogger(getClass)

  val timeout = 10.seconds // TODO jcolomer: extraer a config

  override def sendReminder(
      service: Service,
      request: ReminderRequest,
      withBackoff: Boolean = true
  ): Future[ReminderResponse] = {
    @volatile var nAttempt = 0

    val computeFlow = Flow[ReminderRequest]
      .map { reminderRequest =>
        nAttempt = nAttempt + 1
        reminderRequest.copy(attempt = nAttempt)
      }
      .mapAsync(1) { reminderRequest =>
        Try {
          Uri(service.endpoint)
        }.map { validUri =>
          for {
            requestEntity <- Marshal(reminderRequest).to[RequestEntity]
            _ = logger.info(s"reminder request to be sent: $reminderRequest")
            result <- Http().singleRequest(
              HttpRequest(method = HttpMethods.POST, uri = validUri, entity = requestEntity)
            )
            responseStrict <- result.entity.toStrict(timeout)
            responseAsString = responseStrict.data.utf8String
          } yield {
            if (result.status.isFailure()) {
              throw ReminderRequestFailedError(
                statusCode = result.status,
                endpoint = validUri.toString,
                error = responseAsString
              )
            } else {
              ReminderResponse(responseAsString)
            }
          }
        }.fold(ex => Future.failed(InvalidServiceEndpointError(service.endpoint, ex.getMessage)), identity)
      }

    val restartSourceWithBackoff = RestartSource.onFailuresWithBackoff(
      RestartSettings(
        minBackoff = 1.second,
        maxBackoff = 10.seconds,
        randomFactor = 0.2
      )
    )(() => Source.single(request).via(computeFlow))

    if (withBackoff) {
      val (killSwitch, response) =
        restartSourceWithBackoff.viaMat(KillSwitches.single)(Keep.right).toMat(Sink.head)(Keep.both).run()
      Source
        .single(())
        .delay(service.patienceInMins.minutes)
        .map(_ => killSwitch.abort(ReminderRequestTimedOutError(service.patienceInMins)))
        .run()
      response
    } else {
      Source.single(request).via(computeFlow).toMat(Sink.head)(Keep.right).run()
    }
  }
}
