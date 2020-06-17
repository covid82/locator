val Http4sVersion = "0.21.3"
val CirceVersion = "0.13.0"
val Specs2Version = "4.9.3"
val LogbackVersion = "1.2.3"
val Fs2FtpVersion = "0.4.0"
val NatchezVersion = "0.0.10"
val DoobieVersion = "0.8.6"
val Fs2AwsVersion = "2.28.42"

lazy val root = (project in file("."))
  .settings(
    organization := "com.example",
    name := "locator",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Seq(
      "org.http4s"             %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"             %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"             %% "http4s-circe"        % Http4sVersion,
      "org.http4s"             %% "http4s-dsl"          % Http4sVersion,
      "io.circe"               %% "circe-generic"       % CirceVersion,
      "io.circe"               %% "circe-parser"        % CirceVersion,
      "org.specs2"             %% "specs2-core"         % Specs2Version % "test",
      "ch.qos.logback"         %  "logback-classic"     % LogbackVersion,
      "com.github.regis-leray" %% "fs2-ftp"             % Fs2FtpVersion,
      "org.tpolecat"           %% "natchez-jaeger"      % NatchezVersion,
      "org.tpolecat"           %% "natchez-core"        % NatchezVersion,
      "org.tpolecat"           %% "doobie-core"         % DoobieVersion,
      "org.tpolecat"           %% "doobie-postgres"     % DoobieVersion,
      "io.laserdisc"           %% "fs2-aws"             % Fs2AwsVersion
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
)

assemblyMergeStrategy in assembly := {
  case PathList("org", "slf4j", xs@_*) => MergeStrategy.first
  case PathList("META-INF","io.netty.versions.properties") => MergeStrategy.first
  case PathList("codegen-resources", xs@_*) => MergeStrategy.first
  case PathList("mime.types") => MergeStrategy.first
  case PathList("module-info.class") => MergeStrategy.first
  case x => (assemblyMergeStrategy in assembly).value(x)
}