package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.search.model.ExpectedFailure
import com.sksamuel.elastic4s.ElasticClient
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}

object DepartmentSearchRepo {

  trait Service extends Repository[Any, DepartmentId, Department] with SearchRepository[Any, Department]

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

  final case class EsDepartmentSearchRepoService(
    indexName: String,
    elasticClient: ElasticClient,
    logger: Logger[String])
      extends AbstractCombinedRepository[Any, DepartmentId, Department] with DepartmentSearchRepo.Service {
    override val repository = new ESRepository[Any, DepartmentId, Department](indexName, elasticClient, logger)

    override val searchRepository =
      new ESSearchRepository[Any, Department](
        indexName,
        EsDepartmentSearchRepoService.suggestProperties,
        elasticClient,
        logger)
  }

  object EsDepartmentSearchRepoService {
    import com.sksamuel.elastic4s.ElasticDsl._

    val suggestProperties = Seq("name")

    val fields = Seq(
      textField("id").fielddata(true),
      textField("name").fielddata(true),
      textField("description").fielddata(true)
    ) ++ suggestProperties.map(prop => completionField(ElasticUtils.getSuggestPropertyName(prop)))
  }

  def elasticsearch(indexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, DepartmentSearchRepo] =
    ZLayer.fromServices[ElasticClient, Logger[String], DepartmentSearchRepo.Service] { (elasticClient, logger) =>
      EsDepartmentSearchRepoService(indexName, elasticClient, logger)
    }

  def find(id: DepartmentId): ZIO[DepartmentSearchRepo, ExpectedFailure, Option[Department]] = {
    ZIO.accessM[DepartmentSearchRepo](_.get.find(id))
  }

  def findAll(): ZIO[DepartmentSearchRepo, ExpectedFailure, Seq[Department]] = {
    ZIO.accessM[DepartmentSearchRepo](_.get.findAll())
  }
}
