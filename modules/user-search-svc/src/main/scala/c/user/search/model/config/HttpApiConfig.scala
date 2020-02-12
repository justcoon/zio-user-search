package c.user.search.model.config

import scala.concurrent.duration.FiniteDuration

case class HttpApiConfig(address: String, port: Int, repositoryTimeout: FiniteDuration)
