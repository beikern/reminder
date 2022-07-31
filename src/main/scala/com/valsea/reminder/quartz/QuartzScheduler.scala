package com.valsea.reminder.quartz

import com.valsea.reminder.DBConnectionProvider
import org.quartz.Scheduler
import org.quartz.impl.DirectSchedulerFactory
import org.quartz.impl.jdbcjobstore.JobStoreTX
import org.quartz.simpl.SimpleThreadPool
import org.quartz.utils.{ DBConnectionManager, PoolingConnectionProvider }

import scala.util.Try

object QuartzScheduler {

  def createScheduler(connectionProvider: DBConnectionProvider): Try[Scheduler] =
    connectionProvider.connectionProvider.map { connection =>
      val threadPool = {
        val tp = new SimpleThreadPool(1, 5)
        tp.setThreadNamePrefix("QUARTZ_")
        tp.setMakeThreadsDaemons(true)
        tp
      }

      DirectSchedulerFactory.getInstance.createScheduler(threadPool, createPersistentJobStore(connection))
      val scheduler = DirectSchedulerFactory.getInstance.getScheduler
      scheduler.start()
      scheduler
    }

  def shutdownScheduler(quartzScheduler: Scheduler): Try[Unit] =
    Try(quartzScheduler.shutdown())

  private def createPersistentJobStore(connectionProvider: PoolingConnectionProvider): JobStoreTX = {
    val dataSource = "reminderDS"

    DBConnectionManager.getInstance().addConnectionProvider(dataSource, connectionProvider)

    val jobStore = new JobStoreTX()
    jobStore.setInstanceId("Reminder")
    jobStore.setTablePrefix("QRTZ_")
    jobStore.setIsClustered(true)
    jobStore.setDataSource(dataSource)
    jobStore.setDriverDelegateClass("org.quartz.impl.jdbcjobstore.StdJDBCDelegate")
    jobStore
  }

}
