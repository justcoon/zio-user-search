package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import zio.{Has, ZIO, ZLayer}
import zio.logging.{Logger, Logging}

object UserSearchRepoInit {

  trait Service extends RepositoryInitializer

  object EsUserSearchRepoInitService {
    import com.sksamuel.elastic4s.ElasticDsl._

    val suggestProperties = Seq("username", "email")

    val fields = Seq(
      textField("id").fielddata(true),
      textField("username").fielddata(true),
      textField("email").fielddata(true),
      textField("address.street").fielddata(true),
      textField("address.number").fielddata(true),
      textField("address.city").fielddata(true),
      textField("address.state").fielddata(true),
      textField("address.zip").fielddata(true),
      textField("address.country").fielddata(true),
      booleanField("deleted")
    ) ++ suggestProperties.map(prop => completionField(ElasticUtils.getSuggestPropertyName(prop)))
  }

  def elasticsearch(
    userSearchRepoIndexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepoInit] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepoInit.Service] {
      (elasticClient: ElasticClient, logger: Logger[String]) =>
        new ESRepositoryInitializer(userSearchRepoIndexName, EsUserSearchRepoInitService.fields, elasticClient, logger)
          with Service
    }

  val init: ZIO[UserSearchRepoInit, Throwable, Boolean] = ZIO.accessM[UserSearchRepoInit](_.get.init())
}
