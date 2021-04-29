package com.jc

import zio.Has

package object auth {
  type JwtAuthenticator = Has[JwtAuthenticator.Service]
}
