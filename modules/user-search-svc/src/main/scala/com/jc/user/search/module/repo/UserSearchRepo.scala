package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity.UserId
import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.search.model.ExpectedFailure
import com.sksamuel.elastic4s.ElasticClient
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}

object UserSearchRepo {

  trait Service extends Repository[Any, UserId, User] with SearchRepository[Any, User] {

    def searchByDepartment(
      id: DepartmentId,
      page: Int,
      pageSize: Int): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[User]]
  }

  final case class Department(
    id: DepartmentId,
    name: String = "",
    description: String = ""
  ) extends Repository.Entity[DepartmentId]

  object Department {
    import io.circe._, io.circe.generic.extras.semiauto._, io.circe.generic.extras.Configuration
    implicit val departmentConfig: Configuration = Configuration.default.withDefaults
    implicit val departmentDecoder: Decoder[Department] = deriveConfiguredDecoder[Department]
    implicit val departmentEncoder: Encoder[Department] = deriveConfiguredEncoder[Department]
  }

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
    department: Option[Department] = None
  ) extends Repository.Entity[UserId]

  object User {
    import shapeless._

    val usernameLens: Lens[User, String] = lens[User].username
    val emailLens: Lens[User, String] = lens[User].email
    val passLens: Lens[User, String] = lens[User].pass
    val addressLens: Lens[User, Option[Address]] = lens[User].address
    val departmentLens: Lens[User, Option[Department]] = lens[User].department

    val usernameEmailPassAddressDepartmentLens
      : ProductLensBuilder[User, (String, String, String, Option[Address], Option[Department])] =
      usernameLens ~ emailLens ~ passLens ~ addressLens ~ departmentLens

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
    private val repo = new ESRepository[Any, UserId, User](indexName, elasticClient, logger)

    private val searchRepo =
      new ESSearchRepository[Any, User](indexName, EsUserSearchRepoService.suggestProperties, elasticClient, logger)

    override def insert(value: User): ZIO[Any, ExpectedFailure, Boolean] = repo.insert(value)

    override def update(value: User): ZIO[Any, ExpectedFailure, Boolean] = repo.update(value)

    override def delete(id: UserId): ZIO[Any, ExpectedFailure, Boolean] = repo.delete(id)

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[User]] = repo.find(id)

    override def findAll(): ZIO[Any, ExpectedFailure, Seq[User]] = repo.findAll()

    override def searchByDepartment(
      id: DepartmentId,
      page: Int,
      pageSize: Int): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[User]] = {
      import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchQuery
      import com.sksamuel.elastic4s.requests.searches.sort.FieldSort
      val query = MatchQuery("department.id", id)
      val sorts = Seq(FieldSort("username"))
      searchRepo.search(query, page, pageSize, sorts)
    }

    override def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[SearchRepository.FieldSort])
      : ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[User]] =
      searchRepo.search(query, page, pageSize, sorts)

    override def suggest(query: String): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse] =
      searchRepo.suggest(query)
  }

  object EsUserSearchRepoService {
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
      textField("department.id").fielddata(true),
      textField("department.name").fielddata(true),
      textField("department.description").fielddata(true)
    ) ++ suggestProperties.map(prop => completionField(ElasticUtils.getSuggestPropertyName(prop)))
  }

  def elasticsearch(indexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepo] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepo.Service] { (elasticClient, logger) =>
      EsUserSearchRepoService(indexName, elasticClient, logger)
    }
}
