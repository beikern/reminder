package com.valsea.reminder.repositories

import com.valsea.reminder.aggregates.service.valueObjects.ReminderStatus
import com.valsea.reminder.repositories.model.Reminder
import io.getquill.{ MysqlJdbcContext, SnakeCase }

trait ReminderRepository {
  def getReminder(
      sqlCtx: MysqlJdbcContext[SnakeCase],
      reminderIds: List[String],
      serviceIds: List[String],
      status: Option[ReminderStatus]
  ): List[Reminder]
  def insertReminder(sqlCtx: MysqlJdbcContext[SnakeCase], reminder: Reminder): Unit
  def updateStatus(
      sqlCtx: MysqlJdbcContext[SnakeCase],
      serviceId: String,
      reminderId: String,
      updatedStatus: String
  ): Unit
}

class ReminderRepositoryImpl() extends ReminderRepository {
  override def getReminder(
      sqlCtx: MysqlJdbcContext[SnakeCase],
      reminderIds: List[String],
      serviceIds: List[String],
      status: Option[ReminderStatus]
  ): List[Reminder] = {
    import sqlCtx._

    val resultQuery = if (reminderIds.isEmpty && serviceIds.isEmpty && status.isEmpty) {
      quote {
        query[Reminder]
      }
    } else {
      quote {
        query[Reminder]
          .filter(reminder =>
            liftQuery(reminderIds).contains(reminder.reminderId) &&
              liftQuery(serviceIds).contains(reminder.serviceId)
          )
          .filter(reminder => lift(status.map(_.toString)).filterIfDefined(_ == reminder.status))
      }
    }

    sqlCtx.run(resultQuery)
  }

  override def insertReminder(sqlCtx: MysqlJdbcContext[SnakeCase], reminder: Reminder): Unit = {
    import sqlCtx._

    val insertQuery = quote {
      query[Reminder]
        .insertValue(lift(reminder))
    }
    sqlCtx.run(insertQuery)

    ()
  }

  override def updateStatus(
      sqlCtx: MysqlJdbcContext[SnakeCase],
      serviceId: String,
      reminderId: String,
      updatedStatus: String
  ): Unit = {
    import sqlCtx._

    val updateQuery = quote {
      query[Reminder]
        .filter(reminder => reminder.serviceId == lift(serviceId) && reminder.reminderId == lift(reminderId))
        .update(reminder => reminder.status -> lift(updatedStatus))
    }

    sqlCtx.run(updateQuery)

    ()
  }
}
