package it.pagopa.interop.catalogprocess.common.readmodel

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

final case class TotalCountResult(totalCount: Int)

object TotalCountResult {
  implicit val tcrFormat: RootJsonFormat[TotalCountResult] = jsonFormat1(TotalCountResult.apply)
}
