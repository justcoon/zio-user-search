package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.search.model.ExpectedFailure
import com.sksamuel.elastic4s.ElasticClient

import zio.{ZIO, ZLayer}

trait DepartmentSearchRepo
    extends Repository[Any, DepartmentId, DepartmentSearchRepo.Department]
    with SearchRepository[Any, DepartmentSearchRepo.Department]

final case class EsDepartmentSearchRepo(indexName: String, elasticClient: ElasticClient)
    extends AbstractCombinedRepository[Any, DepartmentId, DepartmentSearchRepo.Department] with DepartmentSearchRepo {

  override val repository =
    new ESRepository[Any, DepartmentId, DepartmentSearchRepo.Department](indexName, elasticClient)

  override val searchRepository =
    new ESSearchRepository[Any, DepartmentSearchRepo.Department](
      indexName,
      EsDepartmentSearchRepo.suggestProperties,
      elasticClient)
}

object EsDepartmentSearchRepo {
  import com.sksamuel.elastic4s.ElasticDsl._

  val suggestProperties = Seq("name")

  val fields = Seq(
    textField("id").fielddata(true),
    textField("name").fielddata(true),
    textField("description").fielddata(true)
  ) ++ suggestProperties.map(prop => completionField(ElasticUtils.getSuggestPropertyName(prop)))

  def layer(indexName: String): ZLayer[ElasticClient, Nothing, EsDepartmentSearchRepo] = {
    ZLayer.fromZIO {
      ZIO.serviceWith[ElasticClient] { elasticClient =>
        EsDepartmentSearchRepo(indexName, elasticClient)
      }
    }
  }
}

object DepartmentSearchRepo {

  final case class Department(
    id: DepartmentId,
    name: String,
    description: String
  ) extends Repository.Entity[DepartmentId]

  object Department {
    import shapeless._

    val nameLens: Lens[Department, String] = lens[Department].name
    val descriptionLens: Lens[Department, String] = lens[Department].description

    val nameDescriptionLens: ProductLensBuilder[Department, (String, String)] = nameLens ~ descriptionLens

    import io.circe._, io.circe.generic.semiauto._

    implicit val departmentDecoder: Decoder[Department] = deriveDecoder[Department]

    implicit val departmentEncoder: Encoder[Department] = new Encoder[Department] {

      val derived: Encoder[Department] = deriveEncoder[Department]

      override def apply(a: Department): Json = {
        derived(a).mapObject { jo => jo.add(ElasticUtils.getSuggestPropertyName("name"), Json.fromString(a.name)) }
      }
    }
  }

  def find(id: DepartmentId): ZIO[DepartmentSearchRepo, ExpectedFailure, Option[Department]] = {
    ZIO.serviceWithZIO[DepartmentSearchRepo](_.find(id))
  }

  def findAll(): ZIO[DepartmentSearchRepo, ExpectedFailure, Seq[Department]] = {
    ZIO.serviceWithZIO[DepartmentSearchRepo](_.findAll())
  }
}
