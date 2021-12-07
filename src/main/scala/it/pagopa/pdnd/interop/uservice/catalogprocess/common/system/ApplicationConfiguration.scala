package it.pagopa.pdnd.interop.uservice.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = config.getInt("application.port")

  def catalogManagementUrl: String           = config.getString("services.catalog-management")
  def agreementManagementUrl: String         = config.getString("services.agreement-management")
  def attributeRegistryManagementUrl: String = config.getString("services.attribute-registry-management")
  def partyManagementUrl: String             = config.getString("services.party-management")
  def storageContainer: String               = config.getString("pdnd-interop-commons.storage.container")

}
