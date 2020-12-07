package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.ExpectedFailure
import com.sksamuel.elastic4s.ElasticClient
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}

object UserSearchRepo {

  trait Service extends Repository[UserId, User] with SearchRepository[User]

  final case class Address(
    street: String,
    number: String,
    zip: String,
    city: String,
    state: String,
    country: String
  )

  object Address {

    import io.circe._, io.circe.generic.semiauto._

    implicit val addressDecoder: Decoder[Address] = deriveDecoder[Address]

    implicit val addressEncoder: Encoder[Address] = deriveEncoder[Address]
  }

  final case class User(
    id: UserId,
    username: String,
    email: String,
    pass: String,
    address: Option[Address] = None,
    deleted: Boolean = false
  ) extends Repository.Entity[UserId]

  object User {
    import shapeless._

    val usernameLens: Lens[User, String] = lens[User].username
    val emailLens: Lens[User, String] = lens[User].email
    val passLens: Lens[User, String] = lens[User].pass
    val addressLens: Lens[User, Option[Address]] = lens[User].address
    val deletedLens: Lens[User, Boolean] = lens[User].deleted

    val usernameEmailPassAddressLens = usernameLens ~ emailLens ~ passLens ~ addressLens

    import io.circe._, io.circe.generic.semiauto._

    implicit val userDecoder: Decoder[User] = deriveDecoder[User]

    implicit val userEncoder: Encoder[User] = new Encoder[User] {

      val derived: Encoder[User] = deriveEncoder[User]

      override def apply(a: User): Json = {
        derived(a).mapObject { jo =>
          jo.add(ElasticUtils.getSuggestPropertyName("username"), Json.fromString(a.username))
            .add(ElasticUtils.getSuggestPropertyName("email"), Json.fromString(a.email))
        }
      }
    }
  }

  final case class EsUserSearchRepoService(indexName: String, elasticClient: ElasticClient, logger: Logger[String])
      extends UserSearchRepo.Service {
    private val suggestProperties = Seq("username", "email")
    private val repo = new ESRepository[UserId, User](indexName, elasticClient, logger)
    private val searchRepo = new ESSearchRepository[User](indexName, suggestProperties, elasticClient, logger)

    override def insert(value: User): ZIO[Any, ExpectedFailure, Boolean] = repo.insert(value)

    override def update(value: User): ZIO[Any, ExpectedFailure, Boolean] = repo.update(value)

    override def delete(id: UserId): ZIO[Any, ExpectedFailure, Boolean] = repo.delete(id)

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[User]] = repo.find(id)

    override def findAll(): ZIO[Any, ExpectedFailure, Seq[User]] = repo.findAll()

    override def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[SearchRepository.FieldSort])
      : ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[User]] =
      searchRepo.search(query, page, pageSize, sorts)

    override def suggest(query: String): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse] =
      searchRepo.suggest(query)
  }

  def elasticsearch(userSearchRepoIndexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepo] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepo.Service] {
      (elasticClient: ElasticClient, logger: Logger[String]) =>
        EsUserSearchRepoService(userSearchRepoIndexName, elasticClient, logger)
    }
}
