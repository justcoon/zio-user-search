package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.ExpectedFailure
import zio.{ULayer, ZIO, ZLayer}

object TestUserSearchRepo {

  class TestUserSearchRepoService extends UserSearchRepo.Service {
    private val repo = InMemoryRepository[Any, UserId, UserSearchRepo.User]()
    private val searchRepo = NoOpSearchRepository[Any, UserSearchRepo.User]()

    override def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[SearchRepository.FieldSort])
      : ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[UserSearchRepo.User]] =
      searchRepo.search(query, page, pageSize, sorts)

    override def suggest(query: String): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse] =
      searchRepo.suggest(query)

    override def insert(value: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = repo.insert(value)

    override def update(value: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = repo.update(value)

    override def delete(id: UserId): ZIO[Any, ExpectedFailure, Boolean] = repo.delete(id)

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]] =
      repo.find(id)

    override def findAll(): ZIO[Any, ExpectedFailure, Seq[UserSearchRepo.User]] = repo.findAll()

    override def searchByDepartment(
      id: DepartmentId,
      page: Int,
      pageSize: Int): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[UserSearchRepo.User]] = {
      repo.find(u => u.department.exists(_.id == id)).map { found =>
        val items = found.drop(page * pageSize).take(pageSize)
        SearchRepository.PaginatedSequence(items, page, pageSize, found.size)
      }
    }
  }

  val test: ULayer[UserSearchRepo] = ZLayer.succeed(new TestUserSearchRepoService)

}
