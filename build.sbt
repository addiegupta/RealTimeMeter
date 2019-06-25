name := "WebServiceForCallandStop"

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
        "com.typesafe.play"       %%  "play"                           % "2.6.21",
        "com.typesafe.akka"       %% "akka-http-core"                  % akkaHttpVersion,
        "com.typesafe.akka"       %% "akka-http"                       % akkaHttpVersion,
        "com.typesafe.akka"       %% "akka-http-spray-json"            % akkaHttpVersion,
        "com.typesafe.akka"       %%  "akka-testkit"                   % akkaVersion   % "test",
        "org.scalatest"           %% "scalatest"                       % "3.0.0"       % "test",
        "com.github.nscala-time"  %% "nscala-time"                     % "2.22.0"
    )
}