name := "RealTimeMeter"

version := "0.1"
scalaVersion := "2.12.6"


resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Sonatype snapshots"  at "http://oss.sonatype.org/content/repositories/snapshots/")


parallelExecution in Test := false

fork := true

libraryDependencies ++= {
    val akkaVersion = "2.4.19"
    val akkaHttpVersion = "10.0.9"
    Seq(
        "com.typesafe.akka"       %%  "akka-actor"                     % akkaVersion,
        "com.typesafe.akka"       %%  "akka-slf4j"                     % akkaVersion,
        "com.typesafe.akka"       %%  "akka-stream"                    % akkaVersion,
        "com.typesafe.akka"       %% "akka-http-core"                  % akkaHttpVersion,
        "com.typesafe.akka"       %% "akka-http"                       % akkaHttpVersion,
        "com.typesafe.akka"       %% "akka-http-spray-json"            % akkaHttpVersion,
        "com.typesafe.akka"       %%  "akka-testkit"                   % akkaVersion   % "test",
        "org.scalatest"           %% "scalatest"                       % "3.0.0"       % "test",
        "com.github.nscala-time"  %% "nscala-time"                     % "2.22.0",
        "com.typesafe.slick"      %% "slick-hikaricp"                  % "3.3.0",
        "com.typesafe.slick"      %% "slick-codegen"                   % "3.3.0",
        "com.typesafe.slick"      %% "slick"                           % "3.3.0",
        "org.scalatest"           %% "scalatest"                       % "3.0.5"        % "test",
        "org.postgresql"          %  "postgresql"                      % "42.2.6",
        "de.aktey.akka.visualmailbox" %% "collector"                   % "1.1.0",
        "ch.qos.logback"          %  "logback-classic"                 % "1.2.3"
    )
}