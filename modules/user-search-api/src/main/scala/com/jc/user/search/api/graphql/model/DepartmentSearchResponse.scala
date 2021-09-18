package com.jc.user.search.api.graphql.model

final case class DepartmentSearchResponse(items: Seq[Department], page: Int, pageSize: Int, count: Int)
