package com.jc.user.search.module.repo

import com.jc.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import io.circe.{Decoder, Encoder}
import zio.ZIO

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import scala.reflect.ClassTag

trait Repository[R, ID, E <: Repository.Entity[ID]] {

  def insert(value: E): ZIO[R, ExpectedFailure, Boolean]

  def update(value: E): ZIO[R, ExpectedFailure, Boolean]

  def delete(id: ID): ZIO[R, ExpectedFailure, Boolean]

  def find(id: ID): ZIO[R, ExpectedFailure, Option[E]]

  def findAll(): ZIO[R, ExpectedFailure, Seq[E]]
}

object Repository {

  trait Entity[ID] {
    def id: ID
  }
}

trait SearchRepository[R, E <: Repository.Entity[_]] {

  def search(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sorts: Iterable[SearchRepository.FieldSort]
  ): ZIO[R, ExpectedFailure, SearchRepository.PaginatedSequence[E]]

  def suggest(
    query: String
  ): ZIO[R, ExpectedFailure, SearchRepository.SuggestResponse]
}

object SearchRepository {

  final case class FieldSort(property: String, asc: Boolean)

  final case class PaginatedSequence[E](items: Seq[E], page: Int, pageSize: Int, count: Int)

  final case class SuggestResponse(items: Seq[PropertySuggestions])

  final case class TermSuggestion(text: String, score: Double, freq: Int)

  final case class PropertySuggestions(property: String, suggestions: Seq[TermSuggestion])

}

abstract class AbstractCombinedRepository[R, ID, E <: Repository.Entity[ID]]
    extends Repository[R, ID, E] with SearchRepository[R, E] {

  protected def repository: Repository[R, ID, E]
  protected def searchRepository: SearchRepository[R, E]

  override def insert(value: E): ZIO[R, ExpectedFailure, Boolean] = repository.insert(value)

  override def update(value: E): ZIO[R, ExpectedFailure, Boolean] = repository.update(value)

  override def delete(id: ID): ZIO[R, ExpectedFailure, Boolean] = repository.delete(id)

  override def find(id: ID): ZIO[R, ExpectedFailure, Option[E]] = repository.find(id)

  override def findAll(): ZIO[R, ExpectedFailure, Seq[E]] = repository.findAll()

  override def search(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sorts: Iterable[SearchRepository.FieldSort]): ZIO[R, ExpectedFailure, SearchRepository.PaginatedSequence[E]] =
    searchRepository.search(query, page, pageSize, sorts)

  override def suggest(query: String): ZIO[R, ExpectedFailure, SearchRepository.SuggestResponse] =
    searchRepository.suggest(query)
}

class ESRepository[R, ID: Encoder: Decoder, E <: Repository.Entity[ID]: Encoder: Decoder: ClassTag](
  indexName: String,
  elasticClient: ElasticClient)
    extends Repository[R, ID, E] {

  import com.jc.user.search.module.es.instances._
  import com.sksamuel.elastic4s.ElasticDsl.{search => searchIndex, _}
  import com.sksamuel.elastic4s.circe._

  override def insert(value: E): ZIO[R, ExpectedFailure, Boolean] = {
    val id = value.id.toString
    ZIO.logDebug(s"insert - ${indexName} - id: ${id}") *>
      elasticClient.execute {
        indexInto(indexName).doc(value).id(id)
      }.map(_.isSuccess).mapError(RepoFailure).tapError { e =>
        ZIO.logError(s"insert - ${indexName} - id: ${value.id} - error: ${e.throwable.getMessage}")
      }
  }

  override def update(value: E): ZIO[R, ExpectedFailure, Boolean] = {
    val id = value.id.toString
    ZIO.logDebug(s"update - ${indexName} - id: ${id}") *>
      elasticClient.execute {
        updateById(indexName, value.id.toString).doc(value)
      }.map(_.isSuccess).mapError(RepoFailure).tapError { e =>
        ZIO.logError(s"update - ${indexName} - id: ${id} - error: ${e.throwable.getMessage}")
      }
  }

  override def delete(id: ID): ZIO[R, ExpectedFailure, Boolean] = {
    ZIO.logDebug(s"update - id: ${id}") *>
      elasticClient.execute {
        deleteById(indexName, id.toString)
      }.map(_.isSuccess).mapError(RepoFailure).tapError { e =>
        ZIO.logError(s"update - id: ${id} - error: ${e.throwable.getMessage}")
      }
  }

  override def find(id: ID): ZIO[R, ExpectedFailure, Option[E]] = {
    ZIO.logDebug(s"find - ${indexName} - id: ${id}") *>
      elasticClient.execute {
        get(indexName, id.toString)
      }.map { r =>
        if (r.result.exists)
          Option(r.result.to[E])
        else
          Option.empty
      }.mapError(RepoFailure).tapError { e =>
        ZIO.logError(s"find - ${indexName} - id: ${id} - error: ${e.throwable.getMessage}")
      }
  }

  override def findAll(): ZIO[R, ExpectedFailure, Seq[E]] = {
    ZIO.logDebug(s"findAll - ${indexName}") *>
      elasticClient.execute {
        searchIndex(indexName).matchAllQuery()
      }.map(_.result.to[E]).mapError(RepoFailure).tapError { e =>
        ZIO.logError(s"findAll - ${indexName} - error: ${e.throwable.getMessage}")
      }
  }
}

class ESSearchRepository[R, E <: Repository.Entity[_]: Encoder: Decoder: ClassTag](
  indexName: String,
  suggestProperties: Seq[String],
  elasticClient: ElasticClient
) extends SearchRepository[R, E] {

  import com.jc.user.search.module.es.instances._
  import com.sksamuel.elastic4s.ElasticDsl.{search => searchIndex, _}
  import com.sksamuel.elastic4s.requests.searches.queries.QueryStringQuery
  import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
  import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
  import com.sksamuel.elastic4s.requests.searches.suggestion
  import com.sksamuel.elastic4s.circe._

  def search(
    query: com.sksamuel.elastic4s.requests.searches.queries.Query,
    page: Int,
    pageSize: Int,
    sorts: Iterable[com.sksamuel.elastic4s.requests.searches.sort.FieldSort])
    : ZIO[R, ExpectedFailure, SearchRepository.PaginatedSequence[E]] = {

    val elasticQuery = searchIndex(indexName).query(query).from(page * pageSize).limit(pageSize).sortBy(sorts)

    val q = elasticClient.show(elasticQuery)

    ZIO.logDebug(s"search - ${indexName} - query: '${q}'") *>
      elasticClient.execute {
        elasticQuery
      }.flatMap { res =>
        if (res.isSuccess) {
          ZIO.succeed {
            val items = res.result.to[E]
            SearchRepository.PaginatedSequence(items, page, pageSize, res.result.totalHits.toInt)
          }
        } else {
          ZIO.fail(new Exception(ElasticUtils.getReason(res.error)))
        }
      }.mapError(RepoFailure).tapError { e =>
        ZIO.logError(s"search - ${indexName} - query: '${q}' - error: ${e.throwable.getMessage}")
      }
  }

  override def search(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sorts: Iterable[SearchRepository.FieldSort]): ZIO[R, ExpectedFailure, SearchRepository.PaginatedSequence[E]] = {
    val q = query.map(QueryStringQuery(_)).getOrElse(MatchAllQuery())
    val ss = sorts.map { case SearchRepository.FieldSort(property, asc) =>
      val o = if (asc) SortOrder.Asc else SortOrder.Desc
      FieldSort(property, order = o)
    }
    ZIO.logDebug(s"search - ${indexName} - query: '${query
      .getOrElse("N/A")}', page: $page, pageSize: $pageSize, sorts: ${sorts.mkString("[", ",", "]")}") *>
      elasticClient.execute {
        searchIndex(indexName).query(q).from(page * pageSize).limit(pageSize).sortBy(ss)
      }.flatMap { res =>
        if (res.isSuccess) {
          ZIO.succeed {
            val items = res.result.to[E]
            SearchRepository.PaginatedSequence(items, page, pageSize, res.result.totalHits.toInt)
          }
        } else {
          ZIO.fail(new Exception(ElasticUtils.getReason(res.error)))
        }
      }.mapError(RepoFailure).tapError { e =>
        ZIO.logError(
          s"search - ${indexName} - query: '${query.getOrElse("N/A")}', page: $page, pageSize: $pageSize, sorts: ${sorts
            .mkString("[", ",", "]")} - error: ${e.throwable.getMessage}")
      }
  }

  override def suggest(query: String): ZIO[R, ExpectedFailure, SearchRepository.SuggestResponse] = {
    // completion suggestion
    val complSuggestions = suggestProperties.map { p =>
      suggestion
        .CompletionSuggestion(ElasticUtils.getSuggestPropertyName(p), ElasticUtils.getSuggestPropertyName(p))
        .prefix(query)
    }

    ZIO.logDebug(s"suggest - ${indexName} - query: '$query'") *>
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
        ZIO.logError(s"suggest - ${indexName} - query: '$query' - error: ${e.throwable.getMessage}")
      }
  }
}

class InMemoryRepository[R, ID, E <: Repository.Entity[ID]](private val store: ConcurrentHashMap[ID, E])
    extends Repository[R, ID, E] {

  override def insert(value: E): ZIO[R, ExpectedFailure, Boolean] = {
    ZIO.succeed {
      val res = Option(store.put(value.id, value))
      res.isDefined
    }
  }

  override def update(value: E): ZIO[R, ExpectedFailure, Boolean] = {
    ZIO.succeed {
      val res = Option(store.put(value.id, value))
      res.isDefined
    }
  }

  override def delete(id: ID): ZIO[R, ExpectedFailure, Boolean] = {
    ZIO.succeed {
      Option(store.remove(id)).isDefined
    }
  }

  override def find(id: ID): ZIO[R, ExpectedFailure, Option[E]] = {
    ZIO.succeed {
      Option(store.get(id))
    }
  }

  override def findAll(): ZIO[R, ExpectedFailure, Seq[E]] = {
    import scala.jdk.CollectionConverters._
    ZIO.succeed {
      store.values().asScala.toSeq
    }
  }

  def find(predicate: E => Boolean): ZIO[R, ExpectedFailure, Seq[E]] = {
    ZIO.succeed {
      val found = scala.collection.mutable.ListBuffer[E]()
      store.forEach {
        makeBiConsumer { (_, e) =>
          if (predicate(e)) {
            found += e
          }
        }
      }
      found.toSeq
    }
  }

  private[this] def makeBiConsumer(f: (ID, E) => Unit): BiConsumer[ID, E] =
    new BiConsumer[ID, E] {
      override def accept(t: ID, u: E): Unit = f(t, u)
    }
}

object InMemoryRepository {

  def apply[R, ID, E <: Repository.Entity[ID]](): InMemoryRepository[R, ID, E] = {
    new InMemoryRepository(new ConcurrentHashMap[ID, E]())
  }
}

class NoOpSearchRepository[R, E <: Repository.Entity[_]]() extends SearchRepository[R, E] {

  override def suggest(query: String): ZIO[R, ExpectedFailure, SearchRepository.SuggestResponse] =
    ZIO.succeed(SearchRepository.SuggestResponse(Nil))

  override def search(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sorts: Iterable[SearchRepository.FieldSort]): ZIO[R, ExpectedFailure, SearchRepository.PaginatedSequence[E]] =
    ZIO.succeed(SearchRepository.PaginatedSequence[E](Nil, page, pageSize, 0))
}

object NoOpSearchRepository {

  def apply[R, E <: Repository.Entity[_]](): NoOpSearchRepository[R, E] = {
    new NoOpSearchRepository()
  }
}
