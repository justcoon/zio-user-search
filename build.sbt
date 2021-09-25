Global / resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
Scope.Global / scalaVersion := "2.13.6"

lazy val Versions = new {
  val kindProjector = "0.13.2"
  val http4s = "0.22.5"
  val zio = "1.0.12"
  val zioInteropCats = "2.5.1.0" // "3.1.1.0"
  val zioKafka = "0.16.0"
  val zioLogging = "0.5.12"
  val zioMetrics = "1.0.12"
  val zioMagic = "0.3.9"
  val elastic4s = "7.14.1"
  val jackson = "2.12.5"
  val circe = "0.14.1"
  val randomDataGenerator = "2.9"
  val pureconfig = "0.16.0"
  val refined = "0.9.27"
  val logback = "1.2.6"
  val grpc = "1.41.0"
  val chimney = "0.6.1"
  val pauldijouJwt = "5.0.0"
  val tapir = "0.18.3"
  val caliban = "1.1.1"

  val scalaTest = "3.2.9"
  val gatling = "3.6.1"
  val gatlingGrpc = "0.11.1"
  val swaggerParser = "2.0.27"

}

lazy val library =
  new {
    // Scala libraries
    val zio = "dev.zio" %% "zio"                                                          % Versions.zio
    val zioStreams = "dev.zio" %% "zio-streams"                                           % Versions.zio
    val zioInteropCats = "dev.zio" %% "zio-interop-cats"                                  % Versions.zioInteropCats
    val zioKafka = "dev.zio" %% "zio-kafka"                                               % Versions.zioKafka
    val zioLoggingSlf4j = "dev.zio" %% "zio-logging-slf4j"                                % Versions.zioLogging
    val zioMetricsPrometheus = "dev.zio" %% "zio-metrics-prometheus"                      % Versions.zioMetrics
    val zioMagic = "io.github.kitlangton" %% "zio-magic"                                  % Versions.zioMagic
    val elastic4sClientEsjava = "com.sksamuel.elastic4s" %% "elastic4s-client-esjava"     % Versions.elastic4s
    val elastic4sEffectZio = "com.sksamuel.elastic4s" %% "elastic4s-effect-zio"           % Versions.elastic4s
    val elastic4sJsonCirce = "com.sksamuel.elastic4s" %% "elastic4s-json-circe"           % Versions.elastic4s
    val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala"     % Versions.jackson
    val http4sCore = "org.http4s" %% "http4s-core"                                        % Versions.http4s
    val http4sDsl = "org.http4s" %% "http4s-dsl"                                          % Versions.http4s
    val http4sBlazeServer = "org.http4s" %% "http4s-blaze-server"                         % Versions.http4s
    val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client"                         % Versions.http4s
    val http4sCirce = "org.http4s" %% "http4s-circe"                                      % Versions.http4s
    val tapirZioHttp4s = "com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server"       % Versions.tapir
    val tapirSwaggerUiHttp4s = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s" % Versions.tapir
    val circeGeneric = "io.circe" %% "circe-generic"                                      % Versions.circe
    val circeGenericExtras = "io.circe" %% "circe-generic-extras"                         % Versions.circe
    val circeYaml = "io.circe" %% "circe-yaml"                                      % Versions.circe
    val pauldijouJwtCirce = "com.pauldijou" %% "jwt-circe"                                % Versions.pauldijouJwt
    val pureconfig = "com.github.pureconfig" %% "pureconfig"                              % Versions.pureconfig
    val refinedPureconfig = "eu.timepit" %% "refined-pureconfig"                          % Versions.refined
    val chimney = "io.scalaland" %% "chimney"                                             % Versions.chimney

    val grpcServices = "io.grpc"     % "grpc-services"     % Versions.grpc
    val grpcNetty = "io.grpc"        % "grpc-netty"        % Versions.grpc
    val grpcNettyShadded = "io.grpc" % "grpc-netty-shaded" % Versions.grpc

    val scalapbRuntime =
      "com.thesamet.scalapb" %% "scalapb-runtime"                             % scalapb.compiler.Version.scalapbVersion % "protobuf"
    val scalapbRuntimeGrpc = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion

    val calibanHttp4s = "com.github.ghostdogpr" %% "caliban-http4s" % Versions.caliban
    val calibanZioHttp = "com.github.ghostdogpr" %% "caliban-zio-http" % Versions.caliban

    val gatlingCharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % Versions.gatling
    val gatlingTest = "io.gatling"              % "gatling-test-framework"    % Versions.gatling
    val gatlingGrpc = "com.github.phisgr"       % "gatling-grpc"              % Versions.gatlingGrpc

    val scalatest = "org.scalatest" %% "scalatest"                             % Versions.scalaTest           % "test"
    val randomDataGenerator = "com.danielasfregola" %% "random-data-generator" % Versions.randomDataGenerator % "test"

    // Java libraries
    val logback = "ch.qos.logback"             % "logback-classic" % Versions.logback
    val swaggerParser = "io.swagger.parser.v3" % "swagger-parser"  % Versions.swaggerParser
  }

lazy val `zio-user-search` =
  project
    .in(file("."))
    .enablePlugins(GitVersioning)
    .aggregate(`core`, `user-search-api`, `user-search-svc`, `user-search-bench`)
    .settings(settings)
    .settings(
      Compile / unmanagedSourceDirectories := Seq.empty,
      Test / unmanagedSourceDirectories    := Seq.empty,
      publishArtifact                      := false
    )

lazy val `core` =
  (project in file("modules/core"))
    .settings(settings)
    .settings(
      addCompilerPlugin("org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full),
      Compile / PB.targets := Seq(
        scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
        scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
      ),
      Compile / guardrailTasks := List(
        ScalaServer(
          file(s"${baseDirectory.value}/src/main/openapi/LoggingSystemOpenApi.yaml"),
          pkg = "com.jc.logging.openapi",
          framework = "http4s",
          tracing = false,
          customExtraction = true
        )
      )
    )
    .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "openapi")
    .settings(
      libraryDependencies ++= Seq(
        // Scala libraries
        library.zio,
        library.zioStreams,
        library.zioInteropCats,
        library.zioLoggingSlf4j,
        library.circeGeneric,
        library.circeGenericExtras,
        library.circeYaml,
        library.pauldijouJwtCirce,
        library.pureconfig,
        library.refinedPureconfig,
        library.http4sCore,
        library.http4sDsl,
        library.http4sBlazeServer,
        library.http4sBlazeClient,
        library.http4sCirce,
        library.scalapbRuntime,
        library.scalapbRuntimeGrpc,
        library.scalatest,
        // Java libraries
        library.logback,
        library.swaggerParser
      )
    )

lazy val `user-search-api` =
  (project in file("modules/user-search-api"))
    .settings(settings)
    .settings(
      addCompilerPlugin("org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full),
      Compile / PB.targets := Seq(
        scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
        scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
      ),
      Compile / guardrailTasks := List(
        ScalaServer(
          file(s"${baseDirectory.value}/src/main/openapi/UserSearchOpenApi.yaml"),
          pkg = "com.jc.user.search.api.openapi",
          imports = List("com.jc.user.domain.circe._"),
          framework = "http4s",
          tracing = false,
          customExtraction = true
        )
      )
    )
    .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "openapi")
    .settings(
      libraryDependencies ++= Seq(
        // Scala libraries
        library.zio,
        library.zioStreams,
        library.zioInteropCats,
        library.zioLoggingSlf4j,
        library.http4sCore,
        library.http4sDsl,
        library.http4sBlazeServer,
        library.http4sBlazeClient,
        library.http4sCirce,
        library.circeGeneric,
        library.circeGenericExtras,
        library.calibanZioHttp,
        library.scalapbRuntime,
        library.scalapbRuntimeGrpc
      )
    )

lazy val `user-search-svc` =
  (project in file("modules/user-search-svc"))
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .settings(settings ++ dockerSettings)
    .settings(
      addCompilerPlugin("org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full)
    )
    .settings(
      libraryDependencies ++= Seq(
        // Scala libraries
        library.zio,
        library.zioStreams,
        library.zioInteropCats,
        library.zioKafka,
        library.zioLoggingSlf4j,
        library.zioMetricsPrometheus,
        library.zioMagic,
        library.elastic4sClientEsjava,
        library.elastic4sEffectZio,
        library.elastic4sJsonCirce,
        library.jacksonModuleScala,
        library.http4sCore,
        library.http4sDsl,
        library.http4sBlazeServer,
        library.http4sBlazeClient,
        library.http4sCirce,
        library.tapirZioHttp4s,
        library.tapirSwaggerUiHttp4s,
        library.circeGeneric,
        library.circeGenericExtras,
        library.pureconfig,
        library.refinedPureconfig,
        library.chimney,
        library.grpcServices,
        library.grpcNetty,
        library.grpcNettyShadded,
        library.scalapbRuntime,
        library.scalapbRuntimeGrpc,
        library.scalatest,
        library.randomDataGenerator,
        // Java libraries
        library.logback
      )
    )
    .aggregate(`user-search-api`)
    .dependsOn(`user-search-api`, `core`)

lazy val `user-search-bench` =
  (project in file("modules/user-search-bench"))
    .enablePlugins(GatlingPlugin)
    .settings(settings)
    .settings(
      addCompilerPlugin("org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full)
    )
    .settings(
      dependencyOverrides += library.scalapbRuntimeGrpc, // gatlig grpc issue
      libraryDependencies ++= Seq(
        library.pureconfig,
        library.refinedPureconfig,
        library.grpcServices,
        library.grpcNetty,
        library.grpcNettyShadded,
        library.scalapbRuntime,
        library.scalapbRuntimeGrpc,
        library.gatlingCharts,
        library.gatlingTest,
        library.gatlingGrpc
      )
    )
    .aggregate(`user-search-api`)
    .dependsOn(`user-search-api`, `core`)

lazy val settings = commonSettings ++ gitSettings

lazy val commonSettings =
  Seq(
    organization             := "c",
    scalafmtOnCompile        := true,
    Test / fork              := true,
    Test / parallelExecution := true,
    licenses += ("Apache 2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    Compile / packageBin / mappings += (ThisBuild / baseDirectory).value / "LICENSE" -> "LICENSE",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "utf-8",
      "-explaintypes",
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
      "-language:experimental.macros", // Allow macro definition (besides implementation and application)
      "-language:higherKinds", // Allow higher-kinded types
      "-language:implicitConversions", // Allow definition of implicit functions called views
      "-unchecked",
      "-Xcheckinit",
      "-Xlint:adapted-args",
      "-Xlint:constant",
      "-Xlint:delayedinit-select",
      "-Xlint:inaccessible",
      "-Xlint:missing-interpolator",
      "-Xlint:nullary-unit",
      "-Xlint:option-implicit",
      "-Xlint:package-object-classes",
      "-Xlint:poly-implicit-overload",
      "-Xlint:private-shadow",
      "-Xlint:stars-align",
      "-Xlint:type-parameter-shadow",
      //"-Xlint:unsound-match",
      //"-Yno-adapted-args",
      //"-Ypartial-unification",
      "-Ywarn-extra-implicit",
      //"-Ywarn-inaccessible",
      //"-Ywarn-nullary-override",
      //"-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )
    //    scalacOptions ++= Seq(
    //      "-unchecked",
    //      "-deprecation",
    //      "-language:_",
    //      "-target:jvm-1.8",
    //      "-encoding",
    //      "UTF-8"
    //    ),
    //    javacOptions ++= Seq(
    //      "-source",
    //      "1.8",
    //      "-target",
    //      "1.8"
    //    )
  )

lazy val gitSettings =
  Seq(
    git.useGitDescribe := true
  )

lazy val dockerSettings =
  Seq(
    Docker / maintainer := "justcoon",
//   Docker / version := "latest",
    dockerUpdateLatest := true,
    dockerExposedPorts := Vector(8000, 8010, 9080),
    dockerBaseImage    := "openjdk:11-jre"
  )
