package it.pagopa.pdnd.interop.uservice.catalogprocess

import com.typesafe.config.{Config, ConfigFactory}

/** Selfless trait containing base test configuration for Akka Cluster Setup
  */
trait SpecConfiguration {

  System.setProperty("AWS_ACCESS_KEY_ID", "foo")
  System.setProperty("AWS_SECRET_ACCESS_KEY", "bar")
  System.setProperty("CATALOG_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("AGREEMENT_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("PARTY_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("ATTRIBUTE_REGISTRY_MANAGEMENT_URL", "http://localhost/")

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("application-test")

  def serviceURL: String = s"${config.getString("application.url")}/${buildinfo.BuildInfo.interfaceVersion}"
  def servicePort: Int   = config.getInt("application.port")
}

object SpecConfiguration extends SpecConfiguration
