
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")
//addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"       % "0.1.8")
addSbtPlugin("au.com.onegeek" % "sbt-dotenv" % "2.1.147")
//addSbtPlugin("org.duhemm"                % "sbt-errors-summary" % "0.6.3")
addSbtPlugin("com.twilio"            % "sbt-guardrail" % "0.58.1.1")
addSbtPlugin("com.typesafe.sbt"      % "sbt-git"       % "1.0.0")
addSbtPlugin("com.thesamet"          % "sbt-protoc"    % "0.99.34")


libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.8"
libraryDependencies += "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.4.0-RC3"
