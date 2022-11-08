package it.pagopa.interop.catalogprocess.common.readmodel

final case class PaginatedResult[A](results: Seq[A], totalCount: Int)
