package com.jc.user.search.api.graphql.model

import com.jc.user.domain.DepartmentEntity.DepartmentId

final case class Department(id: DepartmentId, name: String, description: String)
