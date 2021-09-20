package com.jc.user.search.api.graphql.model

import shapeless.tag.@@

final case class User(
  id: String @@ com.jc.user.domain.UserEntity.UserIdTag,
  username: String,
  email: String,
  pass: String,
  address: Option[Address] = None,
  department: Option[Department] = None)
