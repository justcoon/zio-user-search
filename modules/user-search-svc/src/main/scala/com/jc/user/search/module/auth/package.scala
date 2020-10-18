package com.jc.user.search.module

import zio.Has

package object auth {
  type JwtAuthenticator = Has[JwtAuthenticator.Service]
}
