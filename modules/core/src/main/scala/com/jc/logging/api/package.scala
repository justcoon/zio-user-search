package com.jc.logging

import com.jc.logging.proto.ZioLoggingSystemApi.RCLoggingSystemApiService
import zio.Has

package object api {
  type LoggingSystemGrpcApiHandler = Has[RCLoggingSystemApiService[Any]]
}
