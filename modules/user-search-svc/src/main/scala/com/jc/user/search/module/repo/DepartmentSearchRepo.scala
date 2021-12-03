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
      extends DepartmentSearchRepo.Service {
    private val repo = new ESRepository[Any, DepartmentId, Department](indexName, elasticClient, logger)

    private val searchRepo =
      new ESSearchRepository[Any, Department](
        indexName,
        EsDepartmentSearchRepoService.suggestProperties,
        elasticClient,
        logger)

    override def insert(value: Department): ZIO[Any, ExpectedFailure, Boolean] = repo.insert(value)

    override def update(value: Department): ZIO[Any, ExpectedFailure, Boolean] = repo.update(value)

    override def delete(id: DepartmentId): ZIO[Any, ExpectedFailure, Boolean] = repo.delete(id)

    override def find(id: DepartmentId): ZIO[Any, ExpectedFailure, Option[Department]] = repo.find(id)

    override def findAll(): ZIO[Any, ExpectedFailure, Seq[Department]] = repo.findAll()

    override def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[SearchRepository.FieldSort])
      : ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[Department]] =
      searchRepo.search(query, page, pageSize, sorts)

    override def suggest(query: String): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse] =
      searchRepo.suggest(query)
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
}
