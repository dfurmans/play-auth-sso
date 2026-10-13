organization := "io.ellatech"
name := "play-auth-sso"
version := "0.0.1-SNAPSHOT"

// Strictly Scala 3
scalaVersion := "3.3.7"
val OidcNimbusdsSDKVersion = "11.38.2"

libraryDependencies ++= Seq(
  "org.playframework" %% "play" % "3.0.12" % Provided,
  // OIDC authorization-code flow. Brings nimbus-jose-jwt transitively.
  "com.nimbusds" % "oauth2-oidc-sdk" % OidcNimbusdsSDKVersion,
  // Logging (LazyLogging).
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  "org.playframework" %% "play-guice" % "3.0.12" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
)
// Required Sonatype Publishing Metadata
publishMavenStyle := true
publishTo := sonatypePublishToBundle.value
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
homepage := Some(url("https://github.com/dfurmans/play-auth-sso"))
scmInfo := Some(ScmInfo(url("https://github.com/dfurmans/play-auth-sso"), "scm:git@github.com:dfurmans/play-auth-sso.git"))
developers := List(Developer(id = "dfurmans", name = "dfurmans", email = "dfurmans@proton.me", url = url("https://github.com/dfurmans")))
