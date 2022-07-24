package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import zio.{ULayer, ZLayer}

final class TestDepartmentSearchRepo
    extends AbstractCombinedRepository[Any, DepartmentId, DepartmentSearchRepo.Department] with DepartmentSearchRepo {
  override val repository = InMemoryRepository[Any, DepartmentId, DepartmentSearchRepo.Department]()
  override val searchRepository = NoOpSearchRepository[Any, DepartmentSearchRepo.Department]()
}

object TestDepartmentSearchRepo {
  val layer: ULayer[DepartmentSearchRepo] = ZLayer.succeed(new TestDepartmentSearchRepo)
}
