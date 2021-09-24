package it.pagopa.pdnd.interop.uservice.catalogprocess

import akka.actor.ActorSystem
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.{
  agreementmanagement,
  attributeregistrymanagement,
  catalogmanagement,
  partymanagement
}
import org.apache.commons.io.FileUtils
import org.json4s.{CustomSerializer, JString}

import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID

package object service {
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type PartyManagementInvoker             = partymanagement.client.invoker.ApiInvoker
  type AgreementManagementInvoker         = agreementmanagement.client.invoker.ApiInvoker

  @SuppressWarnings(Array("org.wartremover.warts.PlatformDefault", "org.wartremover.warts.Any"))
  val fileSerializer: CustomSerializer[File] = new CustomSerializer[File](_ =>
    (
      { case JString(s) =>
        val file = Files.createTempFile(UUID.randomUUID().toString, "tmp").toFile
        FileUtils.writeStringToFile(file, s)
        file
      },
      { case file: File => JString(Files.readString(file.toPath, StandardCharsets.US_ASCII)) }
    )
  )
  def decodeHex(text: String): Array[Byte] = {
    new BigInteger(text, 16).toByteArray
  }

  def encodeHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }
  @SuppressWarnings(Array("org.wartremover.warts.PlatformDefault", "org.wartremover.warts.Any"))
  val byteStringSerializer: CustomSerializer[ByteString] = new CustomSerializer[ByteString](_ =>
    ({ case JString(s) => ByteString(decodeHex(s)) }, { case bs: ByteString => JString(encodeHex(bs.toArray)) })
  )

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object AgreementManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all)
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(
        catalogmanagement.client.api.EnumsSerializers.all :+ fileSerializer :+ byteStringSerializer
      )
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object PartyManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyManagementInvoker =
      partymanagement.client.invoker.ApiInvoker(partymanagement.client.api.EnumsSerializers.all)
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object AttributeRegistryManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker.ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all)
  }

}
