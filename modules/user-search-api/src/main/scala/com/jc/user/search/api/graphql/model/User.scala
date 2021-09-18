package com.jc.user.search.api.graphql.model

// FIXME tagged types
//final case class User(
//  id: com.jc.user.domain.UserEntity.UserId,
//  username: String,
//  email: String,
//  pass: String,
//  address: Option[Address] = None,
//  department: Option[Department] = None)

final case class User(
  id: String,
  username: String,
  email: String,
  pass: String,
  address: Option[Address] = None,
  department: Option[Department] = None)
