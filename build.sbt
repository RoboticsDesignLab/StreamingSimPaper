name := "hc-with-streams"

version := "0.1"

scalaVersion := "2.12.9"

val akkaVersion = "2.5.25"

resolvers += "velvia maven" at "http://dl.bintray.com/velvia/maven"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion

libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "1.0.5"
libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "1.1.1"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test

libraryDependencies += "org.velvia" %% "msgpack4s" % "0.6.0"

libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.18"
libraryDependencies += "com.github.stampery" % "msgpack-rpc" % "0.7.1"
libraryDependencies += "org.msgpack" % "jackson-dataformat-msgpack" % "0.8.18"
