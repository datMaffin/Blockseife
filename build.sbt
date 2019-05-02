lazy val akkaHttpVersion = "10.1.8"
lazy val akkaVersion    = "2.5.22"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "de.unistuttgart.stud",
      scalaVersion    := "2.12.8"
    )),
    name := "project-seife",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"      %% "akka-http"                % akkaHttpVersion,
      "com.typesafe.akka"      %% "akka-http-spray-json"     % akkaHttpVersion,
      "com.typesafe.akka"      %% "akka-http-xml"            % akkaHttpVersion,
      "com.typesafe.akka"      %% "akka-stream"              % akkaVersion,
      "com.typesafe.akka"      %% "akka-actor"               % akkaVersion,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",

      "com.typesafe.akka" %% "akka-http-testkit"       % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"            % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"     % akkaVersion     % Test,
      "org.mockito"       %% "mockito-scala"           % "1.4.0-beta.4"  % Test,
      "org.mockito"       %% "mockito-scala-scalatest" % "1.4.0-beta.4"  % Test,
      "org.scalatest"     %% "scalatest"               % "3.0.5"         % Test
    )
  )
