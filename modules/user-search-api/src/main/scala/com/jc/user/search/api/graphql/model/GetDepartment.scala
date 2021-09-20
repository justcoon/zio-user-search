package com.jc.user.search.api.graphql.model

import com.jc.user.domain.DepartmentEntity.DepartmentIdTag
import shapeless.tag.@@

final case class GetDepartment(id: String @@ DepartmentIdTag)
