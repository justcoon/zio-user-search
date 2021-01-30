package com.jc.user.search.module.repo

import com.jc.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import io.circe.{Decoder, Encoder}
import zio.ZIO
import zio.logging.Logger

import scala.reflect.ClassTag

trait Repository[ID, E <: Repository.Entity[ID]] {

  def insert(value: E): ZIO[Any, ExpectedFailure, Boolean]

  def update(value: E): ZIO[Any, ExpectedFailure, Boolean]

  def delete(id: ID): ZIO[Any, ExpectedFailure, Boolean]

  def find(id: ID): ZIO[Any, ExpectedFailure, Option[E]]

  def findAll(): ZIO[Any, ExpectedFailure, Seq[E]]
}

object Repository {

  trait Entity[ID] {
    def id: ID
  }
}

trait SearchRepository[E <: Repository.Entity[_]] {

  def search(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sorts: Iterable[SearchRepository.FieldSort]
  ): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[E]]

  def suggest(
    query: String
  ): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse]
}

object SearchRepository {

  final case class FieldSort(property: String, asc: Boolean)

  final case class PaginatedSequence[E](items: Seq[E], page: Int, pageSize: Int, count: Int)

  final case class SuggestResponse(items: Seq[PropertySuggestions])

  final case class TermSuggestion(text: String, score: Double, freq: Int)

  final case class PropertySuggestions(property: String, suggestions: Seq[TermSuggestion])

}

class ESRepository[ID: Encoder: Decoder, E <: Repository.Entity[ID]: Encoder: Decoder: ClassTag](
  indexName: String,
  elasticClient: ElasticClient,
  logger: Logger[String])
    extends Repository[ID, E] {

  import com.sksamuel.elastic4s.zio.instances._
  import com.sksamuel.elastic4s.ElasticDsl.{search => searchIndex, _}
  import com.sksamuel.elastic4s.circe._

  val serviceLogger = logger.named(getClass.getName)

  override def insert(value: E): ZIO[Any, ExpectedFailure, Boolean] = {
    val id = value.id.toString
    serviceLogger.debug(s"insert - ${indexName} - id: ${id}") *>
      elasticClient.execute {
        indexInto(indexName).doc(value).id(id)
      }.map(_.isSuccess).mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"insert - ${indexName} - id: ${value.id} - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }

  override def update(value: E): ZIO[Any, ExpectedFailure, Boolean] = {
    val id = value.id.toString
    serviceLogger.debug(s"update - ${indexName} - id: ${id}") *>
      elasticClient.execute {
        updateById(indexName, value.id.toString).doc(value)
      }.map(_.isSuccess).mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"update - ${indexName} - id: ${id} - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }

  override def delete(id: ID): ZIO[Any, ExpectedFailure, Boolean] = {
    serviceLogger.debug(s"update - id: ${id}") *>
      elasticClient.execute {
        deleteById(indexName, id.toString)
      }.map(_.isSuccess).mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"update - id: ${id} - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }

  override def find(id: ID): ZIO[Any, ExpectedFailure, Option[E]] = {
    serviceLogger.debug(s"find - ${indexName} - id: ${id}") *>
      elasticClient.execute {
        get(indexName, id.toString)
      }.map { r =>
        if (r.result.exists)
          Option(r.result.to[E])
        else
          Option.empty
      }.mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"find - ${indexName} - id: ${id} - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }

  override def findAll(): ZIO[Any, ExpectedFailure, Seq[E]] = {
    serviceLogger.debug(s"findAll - ${indexName}") *>
      elasticClient.execute {
        searchIndex(indexName).matchAllQuery()
      }.map(_.result.to[E]).mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"findAll - ${indexName} - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }
}

class ESSearchRepository[E <: Repository.Entity[_]: Encoder: Decoder: ClassTag](
  indexName: String,
  suggestProperties: Seq[String],
  elasticClient: ElasticClient,
  logger: Logger[String]
) extends SearchRepository[E] {

  import com.sksamuel.elastic4s.zio.instances._
  import com.sksamuel.elastic4s.ElasticDsl.{search => searchIndex, _}
  import com.sksamuel.elastic4s.requests.searches.queries.QueryStringQuery
  import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
  import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
  import com.sksamuel.elastic4s.requests.searches.suggestion
  import com.sksamuel.elastic4s.circe._

  val serviceLogger = logger.named(getClass.getName)

  def search(
    query: com.sksamuel.elastic4s.requests.searches.queries.Query,
    page: Int,
    pageSize: Int,
    sorts: Iterable[com.sksamuel.elastic4s.requests.searches.sort.FieldSort])
    : ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[E]] = {

    val elasticQuery = searchIndex(indexName).query(query).from(page * pageSize).limit(pageSize).sortBy(sorts)

    val q = elasticClient.show(elasticQuery)

    serviceLogger.debug(s"search - ${indexName} - query: '${q}'") *>
      elasticClient.execute {
        elasticQuery
      }.flatMap { res =>
        if (res.isSuccess) {
          ZIO {
            val items = res.result.to[E]
            SearchRepository.PaginatedSequence(items, page, pageSize, res.result.totalHits.toInt)
          }
        } else {
          ZIO.fail(new Exception(ElasticUtils.getReason(res.error)))
        }
      }.mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"search - ${indexName} - query: '${q}' - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }

  override def search(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sorts: Iterable[SearchRepository.FieldSort]): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[E]] = {
    val q = query.map(QueryStringQuery(_)).getOrElse(MatchAllQuery())
    val ss = sorts.map {
      case SearchRepository.FieldSort(property, asc) =>
        val o = if (asc) SortOrder.Asc else SortOrder.Desc
        FieldSort(property, order = o)
    }
    serviceLogger.debug(s"search - ${indexName} - query: '${query
      .getOrElse("N/A")}', page: $page, pageSize: $pageSize, sorts: ${sorts.mkString("[", ",", "]")}") *>
      elasticClient.execute {
        searchIndex(indexName).query(q).from(page * pageSize).limit(pageSize).sortBy(ss)
      }.flatMap { res =>
        if (res.isSuccess) {
          ZIO {
            val items = res.result.to[E]
            SearchRepository.PaginatedSequence(items, page, pageSize, res.result.totalHits.toInt)
          }
        } else {
          ZIO.fail(new Exception(ElasticUtils.getReason(res.error)))
        }
      }.mapError(RepoFailure).tapError { e =>
        serviceLogger.error(
          s"search - ${indexName} - query: '${query.getOrElse("N/A")}', page: $page, pageSize: $pageSize, sorts: ${sorts
            .mkString("[", ",", "]")} - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }

  override def suggest(query: String): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse] = {
    // completion suggestion
    val complSuggestions = suggestProperties.map { p =>
      suggestion
        .CompletionSuggestion(ElasticUtils.getSuggestPropertyName(p), ElasticUtils.getSuggestPropertyName(p))
        .prefix(query)
    }

    serviceLogger.debug(s"suggest - ${indexName} - query: '$query'") *>
      elasticClient.execute {
        searchIndex(indexName).suggestions(complSuggestions)
      }.flatMap { res =>
        if (res.isSuccess) {
          val elasticSuggestions = res.result.suggestions
          val suggestions = suggestProperties.map { p =>
            val propertySuggestions = elasticSuggestions(ElasticUtils.getSuggestPropertyName(p))
            val suggestions = propertySuggestions.flatMap { v =>
              val r = v.toCompletion
              r.options.map { o => SearchRepository.TermSuggestion(o.text, o.score, o.score.toInt) }
            }

            SearchRepository.PropertySuggestions(p, suggestions)
          }
          ZIO.succeed(SearchRepository.SuggestResponse(suggestions))
        } else {
          ZIO.fail(new Exception(ElasticUtils.getReason(res.error)))
        }
      }.mapError(RepoFailure).tapError { e =>
        serviceLogger.error(s"suggest - ${indexName} - query: '$query' - error: ${e.throwable.getMessage}") *>
          ZIO.fail(e)
      }
  }
}
