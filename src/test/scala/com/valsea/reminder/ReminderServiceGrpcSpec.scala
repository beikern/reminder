//#full-example
package com.valsea.reminder

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.{Config, ConfigFactory}
import com.valsea.reminder.repositories.ReminderRepositoryImpl
import io.getquill.{MysqlJdbcContext, SnakeCase}
import io.grpc.{Status, StatusRuntimeException}
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import com.valsea.reminder.syntaxImplicits._

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Random

class ReminderServiceGrpcSpec extends AnyWordSpec with Matchers with ScalaFutures with RecoverMethods with Eventually {

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  val testConfig: Config = ConfigFactory.parseResources("application-test.conf").resolve()
  implicit val system = ActorSystem[Nothing](Behaviors.empty, "ReminderServerTest", testConfig)

  // Configure the client by code:
  val clientSettings = GrpcClientSettings
    .connectToServiceAt("127.0.0.1", 8083)
    .withTls(
      false
    ) // README jcolomer val reminderService = new ReminderServiceGrpc() was substituted by the GRPC client. IMHO is better in a cluster aware / distributed service like this one.
  val reminderService = ReminderServiceClient(clientSettings)

  val quillCtx: MysqlJdbcContext[SnakeCase] = new MysqlJdbcContext(SnakeCase, "quill-ctx")
  val reminderRepository = new ReminderRepositoryImpl()

  "ReminderServiceGrpc" should {
    "register a service with a valid endpoint" in {
      val service = Service("valid-service", "http://localhost:9090/valid", 5)
      val reply   = reminderService.register(RegisterRequest().withService(service))
      reply.futureValue shouldBe RegisterResponse(s"Service ${service.serviceId} has been registered")
    }

    "register a service with an invalid" in {
      val service                       = Service("valid-service", "http://localhost:9090/invalid", 5)
      val reply                         = reminderService.register(RegisterRequest().withService(service))
      val error: StatusRuntimeException = recoverToExceptionIf[StatusRuntimeException](reply).futureValue

      error.getStatus shouldBe Status.FAILED_PRECONDITION
      error.getTrailers.keys().asScala should contain(
        service.endpoint.normalizeGrpcHeaderKey
      ) // README jcolomer: This assertion had to be modified because of https://chromium.googlesource.com/external/github.com/grpc/grpc/+/HEAD/doc/PROTOCOL-HTTP2.md com.valsea.reminder.grpc.StringSyntax#normalizeGrpcHeaderKey for more details.
    }

    "create a reminder" in {
      val serviceId = UUID.randomUUID().toString
      val service   = Service(serviceId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(service)).futureValue)

      val reminderDate = Timestamp().withSeconds(Instant.now().plusSeconds(120).getEpochSecond)
      val reminder =
        CreateReminderRequest(UUID.randomUUID().toString, service.serviceId, OnlyOnce().withWhen(reminderDate))
      val request = CreateRemindersRequest().withReminders(Seq(reminder))
      val reply   = reminderService.createReminders(request)
      reply.futureValue shouldBe CreateRemindersResponse(
        List(s"Reminder ${reminder.reminderId} in service ${serviceId} has been registered")
      )
    }

    "create same reminder from two different services" in {
      val serviceOneId = UUID.randomUUID().toString
      val serviceOne   = Service(serviceOneId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(serviceOne)).futureValue)

      val serviceTwoId = UUID.randomUUID().toString
      val serviceTwo   = Service(serviceTwoId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(serviceTwo)).futureValue)

      val reminderId   = UUID.randomUUID().toString
      val reminderDate = Timestamp().withSeconds(Instant.now().plusSeconds(120).getEpochSecond)
      val reminderOne =
        CreateReminderRequest(reminderId, serviceOne.serviceId, OnlyOnce().withWhen(reminderDate))
      val reminderTwo =
        CreateReminderRequest(reminderId, serviceTwo.serviceId, OnlyOnce().withWhen(reminderDate))

      val request = CreateRemindersRequest().withReminders(Seq(reminderOne, reminderTwo))
      val reply   = reminderService.createReminders(request)

      reply.futureValue.messages.toList contains theSameElementsAs(
        List(
          s"Reminder ${reminderId} in service ${serviceOneId} has been registered",
          s"Reminder ${reminderId} in service ${serviceTwoId} has been registered"
        )
      )
    }

    "not create a reminder that already exists" in {
      val serviceId = UUID.randomUUID().toString
      val service = Service(serviceId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(service)).futureValue)

      val reminderDate = Timestamp().withSeconds(Instant.now().plusSeconds(120).getEpochSecond)
      val reminderId = UUID.randomUUID().toString
      val reminder =
        CreateReminderRequest(reminderId, service.serviceId, OnlyOnce().withWhen(reminderDate))
      val request = CreateRemindersRequest().withReminders(Seq(reminder))

      val result = for {
        _ <- reminderService.createReminders(request)
        _ <- reminderService.createReminders(request)
      } yield ()

      val error: StatusRuntimeException = recoverToExceptionIf[StatusRuntimeException](result).futureValue
      error.getStatus shouldBe Status.ALREADY_EXISTS
      error.getTrailers.keys().asScala should contain(
        s"$serviceId-$reminderId"
      )
    }

    "not create a reminder if its data exceeds the limit" in {
      val serviceId = UUID.randomUUID().toString
      val service = Service(serviceId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(service)).futureValue)

      val reminderDate = Timestamp().withSeconds(Instant.now().plusSeconds(120).getEpochSecond)
      val reminderId = UUID.randomUUID().toString
      val reminder =
        CreateReminderRequest(reminderId, service.serviceId, OnlyOnce().withWhen(reminderDate), Some(ByteString.copyFrom(getData())))
      val request = CreateRemindersRequest().withReminders(Seq(reminder))
      val reply = reminderService.createReminders(request)

      val error: StatusRuntimeException = recoverToExceptionIf[StatusRuntimeException](reply).futureValue
      error.getStatus shouldBe Status.FAILED_PRECONDITION
      error.getTrailers.keys().asScala should contain(
        s"$serviceId-$reminderId"
      )
    }

    "list reminders" in {
      val serviceId = UUID.randomUUID().toString
      val service = Service(serviceId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(service)).futureValue)

      val reminderDate = Timestamp().withSeconds(Instant.now().plusSeconds(120).getEpochSecond)
      val reminderId = UUID.randomUUID().toString
      val reminder =
        CreateReminderRequest(reminderId, service.serviceId, OnlyOnce().withWhen(reminderDate))
      val request = CreateRemindersRequest().withReminders(Seq(reminder))
      val reply = reminderService.createReminders(request)
      reply.futureValue

      eventually (timeout(Span(10, Seconds))) {
        reminderRepository.getReminder(quillCtx, List(reminderId), List(serviceId), None).size shouldBe 1
      }
    }

    "mark as delivered a reminder that was delivered successfully" in {
      val serviceId = UUID.randomUUID().toString
      val service = Service(serviceId, "http://localhost:9090/valid", 5)
      noException shouldBe thrownBy(reminderService.register(RegisterRequest().withService(service)).futureValue)

      val reminderDate = Timestamp().withSeconds(Instant.now().plusSeconds(5).getEpochSecond)
      val reminderId = UUID.randomUUID().toString
      val reminder =
        CreateReminderRequest(reminderId, service.serviceId, OnlyOnce().withWhen(reminderDate))
      val request = CreateRemindersRequest().withReminders(Seq(reminder))
      val reply = reminderService.createReminders(request)
      reply.futureValue

      eventually(timeout(Span(20, Seconds))) {
        val repoResult = reminderRepository.getReminder(quillCtx, List(reminderId), List(serviceId), None)
        repoResult.size shouldBe 1
        repoResult.head.status shouldBe "DELIVERED"
      }
    }
  }

  /*
    Returns byte array bigger than the data limit size
   */
  def getData() = {
    Random.nextBytes(1024 * 65)
  }
}
