name := "RealTimeMeter"

version := "0.1"
scalaVersion := "2.11.7"


resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Sonatype snapshots"  at "http://oss.sonatype.org/content/repositories/snapshots/")


parallelExecution in Test := false

fork := true

libraryDependencies ++= {
    val akkaVersion = "2.4.1"
    val akkaHttpVersion = "10.0.9"
    Seq(
        "com.typesafe.akka"       %%  "akka-actor"                     % akkaVersion,
        "com.typesafe.slick"      %% "slick-hikaricp"                  % "3.3.0",
        "com.typesafe.slick"      %% "slick-codegen"                   % "3.3.0",
        "com.typesafe.slick"      %% "slick"                           % "3.3.0",
        "org.postgresql"          %  "postgresql"                      % "42.2.6",
        "de.aktey.akka.visualmailbox" %% "collector"                   % "1.1.0",
        "io.spray" %% "spray-can" % "1.3.3",
        "io.spray" %% "spray-routing" % "1.3.3"
    )
}