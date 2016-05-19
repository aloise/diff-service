lazy val root = (project in file(".")).
  settings(
    name := "Assignment4C",
    version := "1.0",
    scalaVersion := "2.11.8"
  )

mainClass in Compile := Some("name.aloise.assignment4c.WebServer")

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.iheart" %% "ficus" % "1.2.5",
  "com.typesafe.akka" %% "akka-http-core" % "2.4.5",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.5",
  "io.spray" %%  "spray-json" % "1.3.2",

  "org.scalactic" %% "scalactic" % "2.2.6",
  "com.typesafe.akka" % "akka-testkit_2.11" % "2.4.5" % Test,
  "org.scalatest" %% "scalatest" % "2.2.6" % Test

)