package com.jc.user.search.module

import zio.Has

package object processor {
  type EventProcessor = Has[EventProcessor.Service]
}
