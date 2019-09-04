name := "hc-with-streams"

version := "0.1"

scalaVersion := "2.12.9"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.24"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.24"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.24"

libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "1.0.5"

libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.18"
libraryDependencies += "com.github.stampery" % "msgpack-rpc" % "0.7.1"
