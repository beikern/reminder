package com.valsea.reminder.projections

import akka.japi.function.Function
import akka.projection.jdbc.JdbcSession
import io.getquill.{ MysqlJdbcContext, SnakeCase }

import java.sql.Connection

final class QuillJDBCSession(val quillCtx: MysqlJdbcContext[SnakeCase]) extends JdbcSession {
  val connection = quillCtx.dataSource.getConnection
  connection.setAutoCommit(false)

  override def withConnection[Result](func: Function[Connection, Result]): Result = func(connection)

  override def commit(): Unit = connection.commit()

  override def rollback(): Unit = connection.rollback()

  override def close(): Unit = connection.close()
}
