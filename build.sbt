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
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.5"
)