
name := "web-crawler"

version := "0.1"

scalaVersion := "2.12.4"

val akkaVersion = "2.5.8"
val akkaHTTPVersion = "10.0.10"
val circeVersion = "0.9.0-M2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" ,
  "com.typesafe.akka" %% "akka-stream",
  "com.typesafe.akka" %% "akka-testkit",
).map(_ % akkaVersion)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHTTPVersion,
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-generic-extras",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "net.ruippeixotog" %% "scala-scraper" % "2.0.0",
  "net.debasishg" %% "redisclient" % "3.4",
  "org.scalatest" % "scalatest_2.12" % "3.0.1" % "test",
  "com.github.pathikrit" %% "better-files" % "3.4.0"
)

assemblyJarName in assembly := "web-crawler.jar"
