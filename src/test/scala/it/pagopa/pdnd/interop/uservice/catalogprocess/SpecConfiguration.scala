package it.pagopa.pdnd.interop.uservice.catalogprocess

import com.typesafe.config.{Config, ConfigFactory}

/** Selfless trait containing base test configuration for Akka Cluster Setup
  */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
trait SpecConfiguration {

  System.setProperty("AWS_ACCESS_KEY_ID", "foo")
  System.setProperty("AWS_SECRET_ACCESS_KEY", "bar")

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("application-test")

  def serviceURL: String = s"${config.getString("application.url")}/${buildinfo.BuildInfo.interfaceVersion}"
  def servicePort: Int   = config.getInt("application.port")
}

object SpecConfiguration extends SpecConfiguration
