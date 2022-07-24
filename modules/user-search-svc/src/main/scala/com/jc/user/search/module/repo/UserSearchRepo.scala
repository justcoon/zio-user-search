package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity.UserId
import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.search.model.ExpectedFailure
import com.sksamuel.elastic4s.ElasticClient

import zio.{ZIO, ZLayer}

trait UserSearchRepo
    extends Repository[Any, UserId, UserSearchRepo.User] with SearchRepository[Any, UserSearchRepo.User] {

  def searchByDepartment(
    id: DepartmentId,
    page: Int,
    pageSize: Int): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[UserSearchRepo.User]]
}

object UserSearchRepo {

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

  def find(id: UserId): ZIO[UserSearchRepo, ExpectedFailure, Option[User]] = {
    ZIO.serviceWithZIO[UserSearchRepo](_.find(id))
  }

  def findAll(): ZIO[UserSearchRepo, ExpectedFailure, Seq[User]] = {
    ZIO.serviceWithZIO[UserSearchRepo](_.findAll())
  }
}

final case class EsUserSearchRepo(indexName: String, elasticClient: ElasticClient)
    extends AbstractCombinedRepository[Any, UserId, UserSearchRepo.User] with UserSearchRepo {
  override val repository = new ESRepository[Any, UserId, UserSearchRepo.User](indexName, elasticClient)

  override val searchRepository =
    new ESSearchRepository[Any, UserSearchRepo.User](indexName, EsUserSearchRepo.suggestProperties, elasticClient)

  override def searchByDepartment(
    id: DepartmentId,
    page: Int,
    pageSize: Int): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[UserSearchRepo.User]] = {
    import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchQuery
    import com.sksamuel.elastic4s.requests.searches.sort.FieldSort
    val query = MatchQuery("department.id", id)
    val sorts = Seq(FieldSort("username"))
    searchRepository.search(query, page, pageSize, sorts)
  }
}

object EsUserSearchRepo {
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

  def make(indexName: String): ZLayer[ElasticClient, Nothing, UserSearchRepo] =
    ZLayer.fromZIO {
      ZIO.serviceWith[ElasticClient] { elasticClient =>
        EsUserSearchRepo(indexName, elasticClient)
      }
    }
}
