package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.Attribute
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{
  AttributeManagementService,
  AttributeRegistryManagementInvoker
}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.ToString"
  )
)
final case class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttribute(attributeId: String): Future[Attribute] = for {
    uuid      <- Future.fromTry(Try(UUID.fromString(attributeId)))
    attribute <- attributeByUUID(uuid)
  } yield attribute


  override def getAttributes(attributeIds: Seq[String]): Future[Seq[Attribute]] = {

  }

  private def attributeByUUID(attributeId: UUID): Future[Attribute] = {
    val request = api.getAttributeById(attributeId) // TODO maybe a batch request is better
    invoker
      .execute(request)
      .map { x =>
        logger.info(s"Retrieving attribute ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving attribute by UUID FAILED: ${ex.getMessage}")
        Future.failed[Attribute](ex)
      }
  }

}
