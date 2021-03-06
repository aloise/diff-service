lazy val root = (project in file(".")).
  settings(
    name := "Assignment4C",
    version := "1.0",
    scalaVersion := "2.11.8"
  )

val jvmRuntimeOptions = Seq(
  "-Dlog4j.skipjansi=true"
)

mainClass in Compile := Some("name.aloise.assignment4c.WebServer")

resolvers += Resolver.jcenterRepo

libraryDependencies ++= {

  val akkaVersion = "2.4.6"

  Seq(
    "com.iheart" %% "ficus" % "1.2.5",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
    "io.spray" %%  "spray-json" % "1.3.2",
    "org.reactivemongo" %% "reactivemongo" % "0.11.11",
    "org.slf4j" % "slf4j-log4j12" % "1.7.21",

    "org.scalatest" %% "scalatest" % "2.2.6" % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % Test,
    "org.scalaj" %% "scalaj-http" % "2.3.0" % Test

  )
}