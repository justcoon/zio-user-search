package com.jc.user.domain

import io.circe.{Decoder, Encoder}
import shapeless.tag
import shapeless.tag.@@

object DepartmentEntity {
  implicit val departmentIdDecoder: Decoder[DepartmentId] = Decoder[String].map(_.asDepartmentId)
  implicit val departmentIdEncoder: Encoder[DepartmentId] = Encoder.encodeString.contramap(identity)

  implicit class DepartmentIdTaggerOps(v: String) {
    val asDepartmentId: DepartmentId = tag[DepartmentIdTag][String](v)

    def as[U]: String @@ U = tag[U][String](v)
  }

  trait DepartmentIdTag

  type DepartmentId = String @@ DepartmentIdTag

  trait DepartmentEvent {
    def entityId: DepartmentId
    def timestamp: java.time.Instant
  }
}
