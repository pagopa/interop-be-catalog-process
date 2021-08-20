package it.pagopa.pdnd.interop.uservice.catalogprocess.common

import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout

import scala.concurrent.duration.DurationInt

package object system {

  implicit val timeout: Timeout = 300.seconds

  object Authenticator extends Authenticator[Seq[(String, String)]] {
    override def apply(credentials: Credentials): Option[Seq[(String, String)]] = Some(Seq.empty[(String, String)])
  }
}
