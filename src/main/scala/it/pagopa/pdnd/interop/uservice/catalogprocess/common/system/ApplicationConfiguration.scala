package it.pagopa.pdnd.interop.uservice.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def catalogManagementUrl: String = {
    val catalogManagementUrl: String = config.getString("services.catalog-management")
    s"$catalogManagementUrl/pdnd-interop-uservice-catalog-management/0.0.1"
  }

}
