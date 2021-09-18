package com.jc.user.search.api.graphql.model

final case class SearchRequest(
  query: Option[String],
  page: Int = 0,
  pageSize: Int = 0,
  sorts: Seq[FieldSort] = Seq.empty)
