package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.search.model.ExpectedFailure
import zio.{ULayer, ZIO, ZLayer}

object TestDepartmentSearchRepo {

  class TestDepartmentSearchRepoService extends DepartmentSearchRepo.Service {
    private val repo = InMemoryRepository[Any, DepartmentId, DepartmentSearchRepo.Department]()
    private val searchRepo = NoOpSearchRepository[Any, DepartmentSearchRepo.Department]()

    override def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[SearchRepository.FieldSort])
      : ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[DepartmentSearchRepo.Department]] =
      searchRepo.search(query, page, pageSize, sorts)

    override def suggest(query: String): ZIO[Any, ExpectedFailure, SearchRepository.SuggestResponse] =
      searchRepo.suggest(query)

    override def insert(value: DepartmentSearchRepo.Department): ZIO[Any, ExpectedFailure, Boolean] = repo.insert(value)

    override def update(value: DepartmentSearchRepo.Department): ZIO[Any, ExpectedFailure, Boolean] = repo.update(value)

    override def delete(id: DepartmentId): ZIO[Any, ExpectedFailure, Boolean] = repo.delete(id)

    override def find(id: DepartmentId): ZIO[Any, ExpectedFailure, Option[DepartmentSearchRepo.Department]] =
      repo.find(id)

    override def findAll(): ZIO[Any, ExpectedFailure, Seq[DepartmentSearchRepo.Department]] = repo.findAll()
  }

  val test: ULayer[DepartmentSearchRepo] = ZLayer.succeed(new TestDepartmentSearchRepoService)

}
