import sbt._
import play.sbt.PlaySettings
import scoverage.ScoverageSbtPlugin._

name := "search-api"
ThisBuild / organization := "org.sunbird"
ThisBuild / scalaVersion := "2.12.8"

lazy val scalaMajorVersion = "2.12"

lazy val root = (project in file("."))
  .aggregate(searchService, searchActors, searchCore)
  .dependsOn(searchService, searchActors, searchCore)
  .settings(
    commonSettings
  )

lazy val searchService = (project in file("search-service"))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayAkkaHttpServer)
  .settings(
    name := "search-service",
    version := "1.0-SNAPSHOT",
    commonSettings,
    libraryDependencies ++= Seq(
      guice,
      "org.joda" % "joda-convert" % "2.1.2",
      "com.github.danielwegener" % "logback-kafka-appender" % "0.2.0-RC2",
      "net.logstash.logback" % "logstash-logback-encoder" % "5.2",
      "io.lemonlabs" %% "scala-uri" % "1.4.10",
      "net.codingwell" %% "scala-guice" % "4.2.5",
      "com.typesafe.play" %% "play-specs2" % "2.7.9",
      "io.netty" % "netty-transport-native-epoll" % "4.1.60.Final",
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
      "org.scalatest" % ("scalatest_" + scalaMajorVersion) % "3.1.2" % Test
    )
  )
  .settings(
    libraryDependencies += ("org.sunbird" % ("search-actors") % "1.0-SNAPSHOT")
      .exclude("com.typesafe.akka","akka-actor_2.11")
      .exclude("org.scala-lang.modules","scala-java8-compat_2.11")
      .exclude("org.scala-lang.modules","scala-parser-combinators_2.11")
      .exclude("com.typesafe.akka","akka-slf4j_2.11")
  ).dependsOn(searchActors)

lazy val searchActors = (project in file("search-actors"))
  .settings(
    name := "search-actors",
    version := "1.0-SNAPSHOT",
    commonSettings,
    libraryDependencies ++= Seq(
      "javax.inject" % "javax.inject" % "1",
      "org.sunbird" % "actor-core" % "1.0-SNAPSHOT",
      "org.sunbird" % ("search-core") % "1.0-SNAPSHOT",
      "org.scalatest" % ("scalatest_" + scalaMajorVersion) % "3.0.8",
      "org.scalamock" % ("scalamock_" + scalaMajorVersion) % "4.4.0" % Test,
      "com.typesafe.akka" % ("akka-testkit_" + scalaMajorVersion) % "2.5.22" % Test,
      "org.javassist" % "javassist" % "3.24.0-GA" % Test,
      "junit" % "junit" % "4.13.1" % Test,
      "org.powermock" % "powermock-api-mockito2" % "2.0.9" % Test,
      "org.powermock" % "powermock-module-junit4" % "2.0.9" % Test
    )
  ).dependsOn(searchCore)

lazy val searchCore = (project in file("search-core"))
  .settings(
    name := "search-core",
    version := "1.0-SNAPSHOT",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" % ("akka-actor_" + scalaMajorVersion) % "2.5.22",
      "org.sunbird" % "platform-common" % "1.0-SNAPSHOT",
      "commons-lang" % "commons-lang" % "2.6",
      "org.apache.httpcomponents" % "httpclient" % "4.5.2",
      "javax.xml" % "jaxb-api" % "2.1",
      "org.sunbird" % "platform-telemetry" % "1.0-SNAPSHOT",
      "org.sunbird" % "schema-validator" % "1.0-SNAPSHOT",
      "org.apache.httpcomponents" % "httpmime" % "4.5.2",
      "net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15",
      "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "6.8.22",
      "junit" % "junit" % "4.13.1" % Test
    )
  )

lazy val commonSettings = Seq(
  javacOptions ++= Seq("-source", "11", "-target", "11"),
  crossPaths := false,
  coverageEnabled := true,
  resolvers ++= Seq("Local Maven Repository" at "file:///"+Path.userHome+"/.m2/repository")
)