package com.jc.user.search.api.graphql.model

final case class SearchRequest(
  query: Option[String],
  page: Int = 0,
  pageSize: Int = 10,
  sorts: Option[Seq[FieldSort]] = None)
