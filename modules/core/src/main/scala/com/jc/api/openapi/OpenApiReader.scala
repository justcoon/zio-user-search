package com.jc.api.openapi

import scala.jdk.CollectionConverters._
import java.nio.file.Path
import java.util
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions

//https://github.com/guardrail-dev/guardrail/blob/master/modules/core/src/main/scala/dev/guardrail/ReadSwagger.scala
object OpenApiReader {

  def read(path: Path): Either[String, OpenAPI] =
    if (path.toFile.exists()) {
      val opts = new ParseOptions()
      opts.setResolve(true)
      val result = new OpenAPIParser().readLocation(path.toAbsolutePath.toString, new util.LinkedList(), opts)
//      Option(result.getMessages()).foreach(_.asScala.foreach(println))
      Option(result.getOpenAPI).toRight(s"Spec file ${path} is incorrectly formatted.")
    } else {
      Left(s"Spec file ${path} does not exist.")
    }

  def read(spec: String): Either[String, OpenAPI] = {
    val opts = new ParseOptions()
    opts.setResolve(true)
    val result = new OpenAPIParser().readContents(spec, new util.LinkedList(), opts)
//    Option(result.getMessages()).foreach(_.asScala.foreach(println))
    Option(result.getOpenAPI).toRight(s"Spec is incorrectly formatted.")
  }
}
