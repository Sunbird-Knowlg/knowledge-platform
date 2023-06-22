import sbt.Keys._
import play.sbt.PlaySettings

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayAkkaHttpServer)
  .settings(
    name := "taxonomy-service-sbt",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.12.8",
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    libraryDependencies ++= Seq(
      guice,
      "org.joda" % "joda-convert" % "2.1.2",
      "net.logstash.logback" % "logstash-logback-encoder" % "5.2",
      "org.sunbird" % "taxonomy-actors" % "1.0-SNAPSHOT",
      "io.lemonlabs" %% "scala-uri" % "1.4.10",
      "net.codingwell" %% "scala-guice" % "4.2.5",
      "com.typesafe.play" %% "play-specs2" % "2.7.9",
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test
    )
  )
  .settings(
    libraryDependencies += ("org.sunbird" % "taxonomy-actors" % "1.0-SNAPSHOT")
      .exclude("com.typesafe.akka","akka-actor_2.11")
      .exclude("org.scala-lang.modules","scala-java8-compat_2.11")
      .exclude("org.scala-lang.modules","scala-parser-combinators_2.11")
      .exclude("com.typesafe.akka","akka-slf4j_2.11")
  )
  resolvers += "Local Maven Repository" at "file:///"+Path.userHome+"/.m2/repository"
