package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import zio.{ULayer, ZLayer}

object TestDepartmentSearchRepo {

  class TestDepartmentSearchRepoService
      extends AbstractCombinedRepository[Any, DepartmentId, DepartmentSearchRepo.Department]
      with DepartmentSearchRepo.Service {
    override val repository = InMemoryRepository[Any, DepartmentId, DepartmentSearchRepo.Department]()
    override val searchRepository = NoOpSearchRepository[Any, DepartmentSearchRepo.Department]()
  }

  val test: ULayer[DepartmentSearchRepo] = ZLayer.succeed(new TestDepartmentSearchRepoService)

}
