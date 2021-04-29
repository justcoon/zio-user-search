package com.jc.aspect

import zio.ZIO

trait Aspect[-R, +E] {
  def apply[R1 <: R, E1 >: E, A](zio: ZIO[R1, E1, A]): ZIO[R1, E1, A]
}

object Aspect {

  implicit final class AspectSyntax[-R, +E, +A](private val zio: ZIO[R, E, A]) {

    def @@[R1 <: R, E1 >: E](aspect: Aspect[R1, E1]): ZIO[R1, E1, A] =
      aspect(zio)
  }
}
