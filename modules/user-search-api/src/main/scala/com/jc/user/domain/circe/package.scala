package com.jc.user.domain

import io.circe.{Decoder, Encoder}
import shapeless.tag
import shapeless.tag.@@

import java.util.UUID

package object circe {

  implicit def taggedDecoder[A: Decoder, T]: Decoder[A @@ T] = implicitly(Decoder[A]).map(tag[T][A])

  implicit def taggedEncoder[A: Encoder, T]: Encoder[A @@ T] = implicitly(Encoder[A]).contramap(identity)

  implicit def uuidTaggedDecoder[T]: Decoder[UUID @@ T] = taggedDecoder[UUID, T]

  implicit def uuidTaggedEncoder[T]: Encoder[UUID @@ T] = taggedEncoder[UUID, T]

  implicit def stringTaggedDecoder[T]: Decoder[String @@ T] = taggedDecoder[String, T]

  implicit def stringTaggedEncoder[T]: Encoder[String @@ T] = taggedEncoder[String, T]

}
