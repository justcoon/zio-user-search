package com.jc.user.search.api

import io.gatling.core.Predef.{stringToExpression => _, _}

object Feeders {
  import com.jc.user.domain.DepartmentEntity._

  val departmentIdFeeder = Array("d1", "d2", "d3", "d4").map(v => Map("id" -> v.asDepartmentId)).random

  val countryFeeder = Array("CA", "UK", "UA", "US", "MX").map(v => Map("id" -> v)).random

  val suggestFeeder = Array("bla", "blue", "red", "bl").map(v => Map("id" -> v)).random
}
