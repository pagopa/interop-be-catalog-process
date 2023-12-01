package it.pagopa.interop.catalogprocess.server.impl

import cats.syntax.all._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import it.pagopa.interop.catalogprocess.service.CatalogManagementService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.utils.CORRELATION_ID_HEADER
import it.pagopa.interop.catalogmanagement.model.{CatalogItem, CatalogDocument}
import it.pagopa.interop.catalogmanagement.client.model.UpdateEServiceDescriptorDocumentSeed
import it.pagopa.interop.catalogprocess.common.system.ApplicationConfiguration

import scala.concurrent.duration.Duration
import java.util.concurrent.{Executors, ExecutorService}
import scala.concurrent.{ExecutionContext, Future, Await, ExecutionContextExecutor}
import it.pagopa.interop.commons.utils.Digester.toSha256

import java.util.UUID
import scala.util.Failure

object Main extends App with Dependencies {

  val logger: Logger = Logger(this.getClass)

  implicit val context: List[(String, String)] = (CORRELATION_ID_HEADER -> UUID.randomUUID().toString()) :: Nil

  implicit val actorSystem: ActorSystem[Nothing]  =
    ActorSystem[Nothing](Behaviors.empty, "interop-be-catalog-process-alignment")
  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  implicit val es: ExecutorService = Executors.newFixedThreadPool(1.max(Runtime.getRuntime.availableProcessors() - 1))
  implicit val blockingEc: ExecutionContextExecutor               = ExecutionContext.fromExecutor(es)
  implicit val catalogManagementService: CatalogManagementService = catalogManagementService(blockingEc)

  implicit val fileManager: FileManager = FileManager.get(FileManager.S3)(blockingEc)

  logger.info("Starting update")
  logger.info(s"Retrieving eservices")
  Await.result(
    execution()
      .andThen { case Failure(ex) => logger.error("Houston we have a problem", ex) }
      .andThen { _ =>
        fileManager.close()
        es.shutdown()
      },
    Duration.Inf
  ): Unit

  logger.info("Completed update")

  def execution(): Future[Unit] = for {
    eservices <- getEservices()
    _ = logger.info(s"Start update eservices ${eservices.size}")
    _ <- eservices.traverse(updateEService)
    _ = logger.info(s"End update eservices")
  } yield ()

  def updateWithFingerprint(eserviceId: UUID, descriptorId: UUID, document: CatalogDocument): Future[Unit] = for {
    fingerprint <- fileManager
      .get(ApplicationConfiguration.storageContainer)(document.path)
      .map(bytes => toSha256(bytes.toByteArray()))
    _           <- catalogManagementService.updateEServiceDocument(
      eServiceId = eserviceId.toString,
      descriptorId = descriptorId.toString,
      documentId = document.id.toString,
      updateEServiceDescriptorDocumentSeed =
        UpdateEServiceDescriptorDocumentSeed(prettyName = document.name, checksum = Some(fingerprint))
    )
  } yield ()

  def updateEService(eservice: CatalogItem): Future[Unit] = {
    logger.info(s"Update eservice ${eservice.id}")
    for {
      _ <- eservice.descriptors.traverse(descriptor =>
        descriptor.interface.traverse(document => updateWithFingerprint(eservice.id, descriptor.id, document))
      )
      _ <- eservice.descriptors.traverse(descriptor =>
        descriptor.docs.traverse(document => updateWithFingerprint(eservice.id, descriptor.id, document))
      )
    } yield ()
  }

  def getEservices(): Future[Seq[CatalogItem]] = getAll(50)(
    catalogManagementService
      .getEServices(None, Seq.empty, Seq.empty, Seq.empty, Seq.empty, None, _, _, false)
      .map(_.results)
  )

  def getAll[T](limit: Int)(get: (Int, Int) => Future[Seq[T]]): Future[Seq[T]] = {
    def go(offset: Int)(acc: Seq[T]): Future[Seq[T]] = {
      get(offset, limit).flatMap(xs =>
        if (xs.size < limit) Future.successful(xs ++ acc)
        else go(offset + xs.size)(xs ++ acc)
      )
    }
    go(0)(Nil)
  }
}
