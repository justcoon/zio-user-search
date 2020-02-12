
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("org.scalameta"             % "sbt-scalafmt"       % "2.2.1")
//addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"       % "0.1.8")
addSbtPlugin("au.com.onegeek"            % "sbt-dotenv"         % "2.1.147")
//addSbtPlugin("org.duhemm"                % "sbt-errors-summary" % "0.6.3")
addSbtPlugin("com.twilio"                % "sbt-guardrail"      % "0.55.2")
addSbtPlugin("com.typesafe.sbt"          % "sbt-git"            % "1.0.0")
addSbtPlugin("com.thesamet"              % "sbt-protoc"         % "0.99.27")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.6"
// https://oss.sonatype.org/#nexus-search;quick~zio-grpc-codegen
libraryDependencies += "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.0.0+6-bc7fd8ab-SNAPSHOT"
