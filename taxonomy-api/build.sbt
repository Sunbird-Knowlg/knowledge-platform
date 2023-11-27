import sbt._
import play.sbt.PlaySettings
import scoverage.ScoverageSbtPlugin._

name := "taxonomy-api"
ThisBuild / organization := "org.sunbird"
ThisBuild / scalaVersion := "2.12.8"

lazy val scalaMajorVersion = "2.12"

lazy val root = (project in file("."))
  .aggregate(taxonomyService, taxonomyActors)
  .dependsOn(taxonomyService, taxonomyActors)
  .settings(
    commonSettings
  )

lazy val taxonomyService = (project in file("taxonomy-service"))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayAkkaHttpServer)
  .settings(
    name := "taxonomy-service",
    version := "1.0-SNAPSHOT",
    commonSettings,
    libraryDependencies ++= Seq(
      guice,
      "org.joda" % "joda-convert" % "2.1.2",
      "net.logstash.logback" % "logstash-logback-encoder" % "5.2",
      "io.lemonlabs" %% "scala-uri" % "1.4.10",
      "net.codingwell" %% "scala-guice" % "4.2.5",
      "com.typesafe.play" %% "play-specs2" % "2.7.9",
      "io.netty" % "netty-transport-native-epoll" % "4.1.60.Final",
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test
    )
  )
  .settings(
    libraryDependencies += ("org.sunbird" % ("taxonomy-actors") % "1.0-SNAPSHOT")
      .exclude("com.typesafe.akka","akka-actor_2.11")
      .exclude("org.scala-lang.modules","scala-java8-compat_2.11")
      .exclude("org.scala-lang.modules","scala-parser-combinators_2.11")
      .exclude("com.typesafe.akka","akka-slf4j_2.11")
  ).dependsOn(taxonomyActors)

lazy val taxonomyActors = (project in file("taxonomy-actors"))
  .settings(
  name := "taxonomy-actors",
  version := "1.0-SNAPSHOT",
  commonSettings,
  libraryDependencies ++= Seq(
    "javax.inject" % "javax.inject" % "1",
    "org.sunbird" % "actor-core" % "1.0-SNAPSHOT",
    "org.sunbird" % ("graph-engine_" + scalaMajorVersion) % "1.0-SNAPSHOT",
    "org.scalatest" % ("scalatest_" + scalaMajorVersion) % "3.0.8",
    "org.scalamock" % ("scalamock_" + scalaMajorVersion) % "4.4.0" % Test,
    "com.typesafe.akka" % ("akka-testkit_" + scalaMajorVersion) % "2.5.22" % Test
  )
  )

lazy val commonSettings = Seq(
  javacOptions ++= Seq("-source", "11", "-target", "11"),
  crossPaths := false,
  coverageEnabled := true,
  resolvers ++= Seq("Local Maven Repository" at "file:///"+Path.userHome+"/.m2/repository")
)
