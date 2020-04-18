package com.jc.user.search.model

sealed abstract class ExpectedFailure extends Exception
case class RepoFailure(throwable: Throwable) extends ExpectedFailure
case class NotFoundFailure(message: String) extends ExpectedFailure

object ExpectedFailure {

  def getMessage(e: ExpectedFailure) = {
    e match {
      case RepoFailure(e) => e.getMessage
      case NotFoundFailure(e) => e
    }
  }
}
