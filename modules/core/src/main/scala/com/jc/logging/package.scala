package com.jc

import zio.Has

package object logging {
  type LoggingSystem = Has[LoggingSystem.Service]
}
