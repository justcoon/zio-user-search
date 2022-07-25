package com.jc.user.search.module.repo

import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.ExpectedFailure
import zio.{ULayer, ZIO, ZLayer}

final class TestUserSearchRepo
    extends AbstractCombinedRepository[Any, UserId, UserSearchRepo.User] with UserSearchRepo {
  override val repository = InMemoryRepository[Any, UserId, UserSearchRepo.User]()
  override val searchRepository = NoOpSearchRepository[Any, UserSearchRepo.User]()

  override def searchByDepartment(
    id: DepartmentId,
    page: Int,
    pageSize: Int): ZIO[Any, ExpectedFailure, SearchRepository.PaginatedSequence[UserSearchRepo.User]] = {
    repository.find(u => u.department.exists(_.id == id)).map { found =>
      val items = found.drop(page * pageSize).take(pageSize)
      SearchRepository.PaginatedSequence(items, page, pageSize, found.size)
    }
  }
}

object TestUserSearchRepo {
  val layer: ULayer[UserSearchRepo] = ZLayer.succeed(new TestUserSearchRepo)
}
