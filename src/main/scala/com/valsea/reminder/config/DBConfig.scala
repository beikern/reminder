package com.valsea.reminder.config

import com.typesafe.config.Config

case class DBConfig(username: String, dbPass: String, dbHost: String, dbName: String, driver: String) {
  lazy val url: String = s"jdbc:mysql://$dbHost/$dbName"
}
object DBConfig {
  def apply(conf: Config): DBConfig = {
    new DBConfig(
      username = conf.getString("mysql-database.user"),
      dbPass = conf.getString("mysql-database.password"),
      dbHost = s"${conf.getString("mysql-database.host")}:${conf.getInt("mysql-database.port")}",
      dbName = conf.getString("mysql-database.database-name"),
      driver = conf.getString("mysql-database.driver")
    )
  }
}
