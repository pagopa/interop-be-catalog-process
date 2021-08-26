package it.pagopa.pdnd.interop.uservice.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int              = config.getInt("application.port")
  def catalogManagementUrl: String = config.getString("services.catalog-management")

}
