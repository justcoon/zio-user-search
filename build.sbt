//scalaVersion := "2.13.1"
//name := "zio-user-search"
//organization := "c.user"
//scalafmtOnCompile := true
//fork in Test := true
//parallelExecution in Test := true

resolvers in Global += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val Versions = new {
  val kindProjector = "0.11.0"
  val scalamacros = "2.1.1"
  val http4s = "0.21.1"
  val zio = "1.0.0-RC17" //"1.0.0-RC17+366-2e305bf4-SNAPSHOT"
  val zioInteropCats = "2.0.0.0-RC10"
  val zioKafka = "0.5.0"
  val zioLogging = "0.2.1"
  val zioMacro = "0.6.2"
  val zioMetrics = "0.1.1"
  val elastic4s = "7.3.5"
  val circe = "0.12.3"
  val scalaTest = "3.0.8"
  val randomDataGenerator = "2.8"
  val pureconfig = "0.12.2"
  val logback = "1.2.3"
  val grpc = "1.26.0"
  val chimney = "0.4.0"
}

lazy val `zio-user-search` =
  project
    .in(file("."))
    .enablePlugins(GitVersioning)
    .aggregate(`user-search-svc`)
    .settings(settings)
    .settings(
      unmanagedSourceDirectories.in(Compile) := Seq.empty,
      unmanagedSourceDirectories.in(Test)    := Seq.empty,
      publishArtifact                        := false
    )

lazy val `user-search-svc` =
  (project in file("modules/user-search-svc"))
    .enablePlugins(Fs2Grpc)
    .settings(settings)
    .settings(
      addCompilerPlugin("org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full),
//      PB.targets in Compile := Seq(
//        scalapb.gen(grpc = true) -> (sourceManaged in Compile).value
////        scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value
//      ),
      guardrailTasks.in(Compile) := List(
        ScalaServer(
          file("modules/user-search-svc/src/main/openapi/UserSearchOpenApi.yaml"),
          pkg = "com.jc.user.search.api.openapi",
          framework = "http4s",
          tracing = false)
      )
    )
    .settings(
      libraryDependencies ++= Seq(
        // Scala libraries
        "dev.zio" %% "zio"                                    % Versions.zio,
        "dev.zio" %% "zio-streams"                            % Versions.zio,
        "dev.zio" %% "zio-interop-cats"                       % Versions.zioInteropCats,
        "dev.zio" %% "zio-kafka"                              % Versions.zioKafka,
        "dev.zio" %% "zio-logging-slf4j"                      % Versions.zioLogging,
        "dev.zio" %% "zio-metrics-prometheus"                 % Versions.zioMetrics,
        "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % Versions.elastic4s,
        "com.sksamuel.elastic4s" %% "elastic4s-effect-zio"    % Versions.elastic4s,
        "com.sksamuel.elastic4s" %% "elastic4s-json-circe"    % Versions.elastic4s,
        "org.http4s" %% "http4s-core"                         % Versions.http4s,
        "org.http4s" %% "http4s-dsl"                          % Versions.http4s,
        "org.http4s" %% "http4s-blaze-server"                 % Versions.http4s,
        "org.http4s" %% "http4s-blaze-client"                 % Versions.http4s,
        "org.http4s" %% "http4s-circe"                        % Versions.http4s,
        "io.circe" %% "circe-generic"                         % Versions.circe,
        "com.github.pureconfig" %% "pureconfig"               % Versions.pureconfig,
        "io.scalaland" %% "chimney"                           % Versions.chimney,
        "io.grpc"                                             % "grpc-services" % Versions.grpc,
        "io.grpc"                                             % "grpc-netty" % Versions.grpc,
        "io.grpc"                                             % "grpc-netty-shaded" % Versions.grpc,
        "com.thesamet.scalapb" %% "scalapb-runtime"           % scalapb.compiler.Version.scalapbVersion % "protobuf",
        "com.thesamet.scalapb" %% "scalapb-runtime-grpc"      % scalapb.compiler.Version.scalapbVersion,
        "org.scalatest" %% "scalatest"                        % Versions.scalaTest % "test",
        "com.danielasfregola" %% "random-data-generator"      % Versions.randomDataGenerator % "test",
        // Java libraries
        "ch.qos.logback" % "logback-classic" % Versions.logback
      )
    )

lazy val settings =
  commonSettings ++
    gitSettings

lazy val commonSettings =
  Seq(
    scalaVersion              := "2.13.1",
    organization              := "c",
    scalafmtOnCompile         := true,
    fork in Test              := true,
    parallelExecution in Test := true,
    licenses += ("Apache 2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    mappings.in(Compile, packageBin) += baseDirectory.in(ThisBuild).value / "LICENSE" -> "LICENSE",
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
      "-Xlint:nullary-override",
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
    //    ),
    //    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    //    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value)
  )

lazy val gitSettings =
  Seq(
    git.useGitDescribe := true
  )
