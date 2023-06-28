package it.pagopa.interop.catalogprocess

import it.pagopa.interop.catalogprocess.api.impl.ProcessApiServiceImpl
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Try}
import it.pagopa.interop.catalogmanagement.model._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

class PublicationEligibilitySpec extends AnyWordSpecLike with SpecConfiguration {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "Publication" must {
    "be eligible" in {
      val descriptor = CatalogDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = Some(
          CatalogDocument(
            id = UUID.randomUUID(),
            name = "fileName",
            contentType = "contentType",
            prettyName = "description",
            path = "path",
            checksum = "checksum",
            uploadDate = OffsetDateTimeSupplier.get().minusDays(10)
          )
        ),
        docs = Seq.empty,
        state = Draft,
        agreementApprovalPolicy = Some(Automatic),
        serverUrls = Nil,
        createdAt = OffsetDateTimeSupplier.get().minusDays(10),
        publishedAt = None,
        deprecatedAt = None,
        suspendedAt = None,
        archivedAt = None,
        attributes = CatalogAttributes.empty
      )

      Await.result(ProcessApiServiceImpl.verifyPublicationEligibility(descriptor), Duration.Inf) shouldBe ()
    }

    "be denied if descriptor is not in Draft status" in {
      val descriptor = CatalogDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = Some(
          CatalogDocument(
            id = UUID.randomUUID(),
            name = "fileName",
            contentType = "contentType",
            prettyName = "description",
            path = "path",
            checksum = "checksum",
            uploadDate = OffsetDateTimeSupplier.get().minusDays(10)
          )
        ),
        docs = Seq.empty,
        state = Published,
        agreementApprovalPolicy = Some(Automatic),
        serverUrls = Nil,
        createdAt = OffsetDateTimeSupplier.get().minusDays(10),
        publishedAt = None,
        deprecatedAt = None,
        suspendedAt = None,
        archivedAt = None,
        attributes = CatalogAttributes.empty
      )

      Try(ProcessApiServiceImpl.verifyPublicationEligibility(descriptor).futureValue) shouldBe a[Failure[_]]
    }

    "be denied if descriptor has no interface" in {
      val descriptor = CatalogDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = None,
        docs = Seq.empty,
        state = Draft,
        agreementApprovalPolicy = Some(Automatic),
        serverUrls = Nil,
        createdAt = OffsetDateTimeSupplier.get().minusDays(10),
        publishedAt = None,
        deprecatedAt = None,
        suspendedAt = None,
        archivedAt = None,
        attributes = CatalogAttributes.empty
      )

      Try(ProcessApiServiceImpl.verifyPublicationEligibility(descriptor).futureValue) shouldBe a[Failure[_]]

    }
  }
}
