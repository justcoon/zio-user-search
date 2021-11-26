package com.jc.user.search.api.graphql.model

final case class User(
  id: com.jc.user.domain.UserEntity.UserId,
  username: String,
  email: String,
  pass: String,
  address: Option[Address] = None,
  department: Option[Department] = None)
