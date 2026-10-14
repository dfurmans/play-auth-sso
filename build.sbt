// Root Plugin Build Definition
organization := "io.github.dfurmans"
name := "play-auth-sso"
scalaVersion := "3.3.7"

val OidcNimbusdsSDKVersion = "11.38.2"

libraryDependencies ++= Seq(
  "org.playframework" %% "play" % "3.0.12" % Provided,
  "com.nimbusds" % "oauth2-oidc-sdk" % OidcNimbusdsSDKVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  "org.playframework" %% "play-guice" % "3.0.12" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
)

lazy val play_auth_sso = (project in file("."))
  .settings(
    name := "play-auth-sso",
    scalaVersion := "3.3.7",
    // Publishing metadata required by Maven Central
    publishMavenStyle := true,
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/dfurmans/play-auth-sso")),
    scmInfo := Some(ScmInfo(url("https://github.com/dfurmans/play-auth-sso"), "scm:git@github.com:dfurmans/play-auth-sso.git")),
    developers := List(Developer(id = "dfurmans", name = "dfurmans", email = "dfurmans@proton.me", url = url("https://github.com/dfurmans")))
  )

ThisBuild / versionScheme := Some("early-semver")

lazy val example = (project in file("examples/quickstart"))
  .enablePlugins(PlayScala)
  .dependsOn(play_auth_sso)
  .settings(
    scalacOptions := Seq("-encoding", "UTF-8", "-feature")
  )