sbtPlugin := true


name := "jscompiler-sbt"

organization := "jscompiler"

version := "0.0.1-SNAPSHOT"

libraryDependencies += "org.mozilla" % "rhino" % "1.7R3"


scalacOptions += "-deprecation"

