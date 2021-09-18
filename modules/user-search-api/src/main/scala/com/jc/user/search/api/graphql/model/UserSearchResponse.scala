package com.jc.user.search.api.graphql.model

case class UserSearchResponse(items: Seq[User], page: Int, pageSize: Int, count: Int)
