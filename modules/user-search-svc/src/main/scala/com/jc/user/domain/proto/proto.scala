package com.jc.user.domain

import com.jc.user.domain.UserEntity.UserId
import com.jc.user.domain.UserEntity._
import scalapb.{TypeMapper, UnknownFieldSet}
import com.google.protobuf.timestamp.Timestamp
import java.time.Instant

package object proto {
  implicit val userIdTypeMapper: TypeMapper[String, UserId] = TypeMapper[String, UserId](_.asUserId)(identity)

  implicit val instantTypeMapper: TypeMapper[Timestamp, Instant] = TypeMapper[Timestamp, Instant] { timestamp =>
    Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos.toLong)
  } { instant => Timestamp.of(instant.getEpochSecond, instant.getNano) }
}
