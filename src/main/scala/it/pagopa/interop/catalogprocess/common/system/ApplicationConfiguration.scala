package it.pagopa.interop.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.cqrs.model.ReadModelConfig

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("catalog-process.port")

  val catalogManagementUrl: String       = config.getString("catalog-process.services.catalog-management")
  val authorizationManagementUrl: String = config.getString("catalog-process.services.authorization-management")

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
