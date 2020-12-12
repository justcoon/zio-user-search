package com.jc.user.domain

import com.jc.user.domain.UserEntity._
import com.jc.user.domain.DepartmentEntity._
import scalapb.{TypeMapper, UnknownFieldSet}
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant

package object proto {
  implicit val userIdTypeMapper: TypeMapper[String, UserId] = TypeMapper[String, UserId](_.asUserId)(identity)

  implicit val departmentIdTypeMapper: TypeMapper[String, DepartmentId] =
    TypeMapper[String, DepartmentId](_.asDepartmentId)(identity)

  implicit val instantTypeMapper: TypeMapper[Timestamp, Instant] = TypeMapper[Timestamp, Instant] { timestamp =>
    Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos.toLong)
  } { instant => Timestamp.of(instant.getEpochSecond, instant.getNano) }
}
