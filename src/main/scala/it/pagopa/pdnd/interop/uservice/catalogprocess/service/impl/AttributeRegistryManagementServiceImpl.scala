package it.pagopa.pdnd.interop.uservice.catalogprocess.service.impl

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.invoker.ApiRequest
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.{Attribute, AttributesResponse}
import it.pagopa.pdnd.interop.uservice.catalogprocess.service.{
  AttributeRegistryManagementInvoker,
  AttributeRegistryManagementService
}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.ToString"
  )
)
final case class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(
  implicit ec: ExecutionContext
) extends AttributeRegistryManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttributesBulk(attributeIds: Seq[String]): Future[Seq[Attribute]] = {
    val request: ApiRequest[AttributesResponse] = api.getBulkedAttributes(Some(attributeIds.mkString(",")))
    invoker
      .execute(request)
      .map { x =>
        logger.info(s"Retrieving attributes using bulked request ${x.code} > ${x.content}")
        x.content.attributes
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving attributes using bulked request FAILED: ${ex.getMessage}")
        Future.failed[Seq[Attribute]](ex)
      }
  }

}
