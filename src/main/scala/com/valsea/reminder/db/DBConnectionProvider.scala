package com.valsea.reminder

import com.valsea.reminder.config.DBConfig
import org.quartz.utils.{ HikariCpPoolingConnectionProvider, PoolingConnectionProvider }

import scala.util.Try

trait DBConnectionProvider {
  def connectionProvider: Try[PoolingConnectionProvider]
}

object DBConnectionProvider {

  class HikariDataSource(dbConfig: DBConfig) extends DBConnectionProvider {
    override def connectionProvider: Try[PoolingConnectionProvider] =
      Try {
        new HikariCpPoolingConnectionProvider(
          dbConfig.driver,
          dbConfig.url,
          dbConfig.username,
          dbConfig.dbPass,
          10,
          "SELECT 1;"
        )
      }
  }
}
