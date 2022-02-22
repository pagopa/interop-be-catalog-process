package it.pagopa.interop.catalogprocess

import com.typesafe.config.{Config, ConfigFactory}

/** Selfless trait containing base test configuration for Akka Cluster Setup
  */
trait SpecConfiguration {

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("application-test")

  def serviceURL: String = s"${config.getString("catalog-process.url")}/${buildinfo.BuildInfo.interfaceVersion}"
  def servicePort: Int   = config.getInt("catalog-process.port")
}

object SpecConfiguration extends SpecConfiguration
