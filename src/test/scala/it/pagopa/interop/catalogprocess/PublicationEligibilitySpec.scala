package it.pagopa.interop.catalogprocess

import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.catalogprocess.api.impl.ProcessApiServiceImpl
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Try}

class PublicationEligibilitySpec extends AnyWordSpecLike with SpecConfiguration {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "Publication" must {
    "be eligible" in {
      val descriptor = CatalogManagementDependency.EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = Some(
          CatalogManagementDependency.EServiceDoc(
            id = UUID.randomUUID(),
            name = "fileName",
            contentType = "contentType",
            description = "description",
            path = "path"
          )
        ),
        docs = Seq.empty,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT
      )

      Await.result(ProcessApiServiceImpl.verifyPublicationEligibility(descriptor), Duration.Inf) shouldBe ()
    }

    "be denied if descriptor is not in Draft status" in {
      val descriptor = CatalogManagementDependency.EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = Some(
          CatalogManagementDependency.EServiceDoc(
            id = UUID.randomUUID(),
            name = "fileName",
            contentType = "contentType",
            description = "description",
            path = "path"
          )
        ),
        docs = Seq.empty,
        state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED
      )

      Try(ProcessApiServiceImpl.verifyPublicationEligibility(descriptor).futureValue) shouldBe a[Failure[_]]
    }

    "be denied if descriptor has no interface" in {
      val descriptor = CatalogManagementDependency.EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "1",
        description = None,
        audience = Seq.empty,
        voucherLifespan = 0,
        dailyCallsPerConsumer = 0,
        dailyCallsTotal = 0,
        interface = None,
        docs = Seq.empty,
        state = CatalogManagementDependency.EServiceDescriptorState.DRAFT
      )

      Try(ProcessApiServiceImpl.verifyPublicationEligibility(descriptor).futureValue) shouldBe a[Failure[_]]

    }
  }
}
