sbtPlugin := true


name := "jscompiler-sbt"

organization := "ws.nexus"

version := "0.0.2"

libraryDependencies += "org.mozilla" % "rhino" % "1.7R3"


scalacOptions += "-deprecation"

