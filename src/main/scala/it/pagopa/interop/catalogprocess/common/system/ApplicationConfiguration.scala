package it.pagopa.interop.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.CollectionHasAsScala

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("catalog-process.port")

  lazy val catalogManagementUrl: String = config.getString("catalog-process.services.catalog-management")

  lazy val agreementManagementUrl: String = config.getString("catalog-process.services.agreement-management")

  lazy val authorizationManagementUrl: String = config.getString("catalog-process.services.authorization-management")

  lazy val attributeRegistryManagementUrl: String =
    config.getString("catalog-process.services.attribute-registry-management")

  lazy val partyManagementUrl: String = config.getString("catalog-process.services.party-management")

  lazy val jwtAudience: Set[String] = config.getStringList("catalog-process.jwt.audience").asScala.toSet

  lazy val storageContainer: String = config.getString("catalog-process.storage.container")

}
