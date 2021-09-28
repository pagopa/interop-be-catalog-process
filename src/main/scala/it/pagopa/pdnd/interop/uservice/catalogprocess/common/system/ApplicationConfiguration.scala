package it.pagopa.pdnd.interop.uservice.catalogprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = 8089 // config.getInt("application.port")

  def catalogManagementUrl: String           = config.getString("services.catalog-management")
  def agreementManagementUrl: String         = config.getString("services.agreement-management")
  def attributeRegistryManagementUrl: String = config.getString("services.attribute-registry-management")
  def partyManagementUrl: String             = config.getString("services.party-management")

  def bucketName: String =
    config.getString("pdnd-uservice-catalog-process.aws.s3-bucket-name")

  def awsCredentials: AwsBasicCredentials =
    AwsBasicCredentials.create(
      config.getString("pdnd-uservice-catalog-process.aws.access-key-id"),
      config.getString("pdnd-uservice-catalog-process.aws.secret-access-key")
    )

}
