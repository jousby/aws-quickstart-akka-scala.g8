// The file <project root>/project/Dependencies.scala contains a full list of all the AWS apis you can use in your
// libraryDependencies section below. You can also update the version of the AWS libs in this file as well.
import Dependencies._

// Project definition
lazy val root = project
  .in(file("."))
  .settings(
    inThisBuild(List(
      name := "$name$",
      organization := "com.example",
      scalaVersion := "$scala_version$",
      version      := "$version$",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
    )),
    libraryDependencies ++= Seq(
      akkaActor,
      akkaSlf4j,
      awsS3,
      logback,
      akkaTestKit % Test,
      scalaMock % Test,
      scalaTest % Test
    )
  )