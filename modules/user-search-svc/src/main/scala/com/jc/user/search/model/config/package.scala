package com.jc.user.search.model

import eu.timepit.refined.api.{RefType, Refined}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.IPv4

package object config {
  type Addresses = List[String] Refined NonEmpty
  type IpAddress = String Refined IPv4
  type TopicName = String Refined NonEmpty
  type IndexName = String Refined NonEmpty

//  implicit def autoUnwrapIterable[TF[_, _], EF[_, _], I[*] <: Iterable[*], E, TP, EP](
//    tp: TF[I[EF[E, EP]], TP]
//  )(implicit trt: RefType[TF], ert: RefType[EF]): I[E] =
//    trt
//      .unwrap(tp)
//      .map { ep =>
//        ert.unwrap(ep)
//      }
//      .asInstanceOf[I[E]]
}
