logLevel := Level.Warn

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.2")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.2")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.2.1")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.12")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.5")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")