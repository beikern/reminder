package com.valsea.reminder.quartz

import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.valsea.reminder.http.ReminderHttpService

import scala.concurrent.ExecutionContext

case class QuartzRuntimeDependencies(
    reminderHttpService: ReminderHttpService,
    sharding: ClusterSharding,
    askTimeout: Timeout,
    blockingEc: ExecutionContext
)
