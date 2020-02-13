package c.user.search.model

sealed abstract class ExpectedFailure extends Exception
case class RepoFailure(throwable: Throwable) extends ExpectedFailure
case class NotFoundFailure(message: String) extends ExpectedFailure
