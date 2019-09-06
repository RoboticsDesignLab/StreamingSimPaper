name := "hc-with-streams"

version := "0.1"

scalaVersion := "2.12.9"

val akkaVersion = "2.5.25"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion

libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "1.0.5"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test

libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.18"
libraryDependencies += "com.github.stampery" % "msgpack-rpc" % "0.7.1"
libraryDependencies += "org.msgpack" % "jackson-dataformat-msgpack" % "0.8.18"
