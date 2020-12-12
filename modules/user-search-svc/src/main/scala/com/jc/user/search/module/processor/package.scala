package com.jc.user.search.module

import zio.Has

package object processor {
  type UserEventProcessor = Has[UserEventProcessor.Service]
  type DepartmentEventProcessor = Has[DepartmentEventProcessor.Service]
}
