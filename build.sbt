name := "weather-rpc"
organization in ThisBuild := "dopenkov"
scalaVersion in ThisBuild := "2.12.9"

// PROJECTS

lazy val global = project
  .in(file("."))
  .settings(settings)
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    protocol,
    client,
    server
  )

lazy val protocol = project
  .settings(
    name := "protocol",
    commonSettings,
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    libraryDependencies ++= commonDependencies ++ Seq(
      dependencies.grpc,
      dependencies.scalapbRuntime,
    ),
  )
  .disablePlugins(AssemblyPlugin)

lazy val client = project
  .settings(
    name := "client",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      dependencies.catsCore,
      dependencies.catsEffect,
    )
  )
  .dependsOn(
    protocol
  )

lazy val server = project
  .settings(
    name := "server",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      dependencies.catsCore,
      dependencies.catsEffect,
      dependencies.pureconfig,
      dependencies.http4s,
      dependencies.http4sClient,
      dependencies.http4sCirce,
      dependencies.enumeratum,
      dependencies.slf4jJava,
      dependencies.circeParser % "test",
    )
  )
  .dependsOn(
    protocol
  )

// DEPENDENCIES
val http4sVersion = "0.20.11"
lazy val dependencies =
  new {
    val typesafeConfig = "com.typesafe" % "config" % "1.3.1" withSources()
    val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.12.1" withSources()
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.4" withSources()
    val catsCore = "org.typelevel" %% "cats-core" % "1.6.1" withSources()
    val catsEffect = "org.typelevel" %% "cats-effect" % "1.3.1" withSources()
    val enumeratum = "com.beachape" %% "enumeratum" % "1.5.13" withSources()
    val grpc = "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion
    val scalapbRuntime = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    val http4s = "org.http4s" %% "http4s-dsl" % http4sVersion withSources()
    val http4sClient = "org.http4s" %% "http4s-blaze-client" % http4sVersion withSources()
    val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion withSources()
    val slf4jJava = "org.slf4j" % "slf4j-jdk14" % "1.7.25" % "runtime" withSources()
    val circeParser = "io.circe" %% "circe-parser" % "0.11.1" withSources()
  }

lazy val commonDependencies = Seq(
  dependencies.typesafeConfig,
  dependencies.scalatest  % "test",
)

// SETTINGS

lazy val settings =
commonSettings ++
wartremoverSettings

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-Ypartial-unification",
  "-Xlog-implicits",
  "-encoding",
  "utf8",
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  /*resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )*/
)

lazy val wartremoverSettings = Seq(
  wartremoverWarnings in (Compile, compile) ++= Warts.allBut(Wart.Throw)
)

lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)
