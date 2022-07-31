package com.valsea.reminder

//#import

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.valsea.reminder.DBConnectionProvider.HikariDataSource
import com.valsea.reminder.config.DBConfig
import com.valsea.reminder.grpc.ReminderServiceGrpc
import com.valsea.reminder.http.ReminderHttpServiceImpl
import com.valsea.reminder.projections.reminder.db.ReminderDBProjection
import com.valsea.reminder.projections.reminder.quartz.ReminderQuartzProjection
import com.valsea.reminder.quartz.QuartzRuntimeDependencies
import com.valsea.reminder.repositories.ReminderRepositoryImpl
import io.getquill.{MysqlJdbcContext, SnakeCase}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ReminderServer {

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.parseResources("application.conf").resolve()
    val system = ActorSystem[Nothing](Behaviors.empty, "ReminderServer", conf)
    new ReminderServer(system, conf).run()
  }
}

class ReminderServer(system: ActorSystem[_], conf: Config) {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(): Unit = {
    implicit val sys                  = system
    implicit val ec: ExecutionContext = system.executionContext

    val quillCtx: MysqlJdbcContext[SnakeCase] = new MysqlJdbcContext(SnakeCase, "quill-ctx")

    val dbConnectionProvider = new HikariDataSource(DBConfig(conf))
    val reminderHttpService = new ReminderHttpServiceImpl(sys)
    val reminderRepository  = new ReminderRepositoryImpl()

    val sharding: ClusterSharding = ClusterSharding(system)
    val quartzBlockingEc = system.dispatchers.lookup(DispatcherSelector.fromConfig("quartz-blocking-dispatcher"))

    // Initializing aggregates
    aggregates.initializeAggregates(sys)

    val quartzRuntimeDependencies = QuartzRuntimeDependencies(reminderHttpService, sharding = sharding, askTimeout = Timeout(10.seconds), blockingEc = quartzBlockingEc)

    // Initializing projections
    ReminderDBProjection.init(system, quillCtx, reminderRepository)
    ReminderQuartzProjection.init(system, quillCtx, dbConnectionProvider, quartzRuntimeDependencies)

    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    val service: HttpRequest => Future[HttpResponse] =
      ReminderServiceHandler(new ReminderServiceGrpc(sys, reminderHttpService, quillCtx, reminderRepository))

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = "127.0.0.1", port = 8083)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info("gRPC server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        logger.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
