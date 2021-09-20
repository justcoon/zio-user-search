package com.jc.user.search.api.graphql.model

import shapeless.tag.@@

final case class GetUser(id: String @@ com.jc.user.domain.UserEntity.UserIdTag)
