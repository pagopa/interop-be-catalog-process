package it.pagopa.interop.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.cqrs.model.ReadModelConfig

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("catalog-process.port")

  val catalogManagementUrl: String           = config.getString("catalog-process.services.catalog-management")
  val agreementManagementUrl: String         = config.getString("catalog-process.services.agreement-management")
  val authorizationManagementUrl: String     = config.getString("catalog-process.services.authorization-management")
  val attributeRegistryManagementUrl: String =
    config.getString("catalog-process.services.attribute-registry-management")
  val partyManagementUrl: String             = config.getString("catalog-process.services.party-management")
  val tenantManagementUrl: String            = config.getString("catalog-process.services.tenant-management")

  val partyManagementApiKey: String = config.getString("catalog-process.api-keys.party-management")

  val jwtAudience: Set[String] = config.getString("catalog-process.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val storageKind: String = config.getString("catalog-process.storage.kind")

  val storageContainer: String = config.getString("catalog-process.storage.container")

  val readModelConfig: ReadModelConfig = {
    val connectionString: String = config.getString("catalog-process.read-model.db.connection-string")
    val dbName: String           = config.getString("catalog-process.read-model.db.name")

    ReadModelConfig(connectionString, dbName)
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")

}
