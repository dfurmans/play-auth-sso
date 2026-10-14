name := "quickstart-app"
organization := "io.ellatech"
version := "1.0.0-SNAPSHOT"
scalaVersion := "3.3.7"

// Notice we do NOT redeclare lazy val root = (project in file(".")) here
// because it's managed as a subproject from the root build file!

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test