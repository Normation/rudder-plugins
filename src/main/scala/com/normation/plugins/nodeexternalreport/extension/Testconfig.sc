package com.normation.plugins.nodeexternalreport.extension


import com.typesafe.config._
import scala.collection.JavaConverters.asScalaSetConverter


object Testconfig {
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  
  val c = ConfigFactory.parseURL(this.getClass.getClassLoader.getResource("externalReports.conf"))
                                                  //> com.typesafe.config.ConfigException$Parse: /home/fanf/java/workspaces/rudder
                                                  //| -project/rudder-plugin-external-node-report/target/classes/externalReports.c
                                                  //| onf: 16: Key 'node' may not be followed by token: '}' (if you intended '}' t
                                                  //| o be part of the value for 'node', try enclosing the value in double quotes,
                                                  //|  or you may be able to rename the file .properties rather than .conf)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.parseError(Parser.java:3
                                                  //| 29)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.parseError(Parser.java:3
                                                  //| 25)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.parseObject(Parser.java:
                                                  //| 643)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.parseValue(Parser.java:4
                                                  //| 08)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.consolidateValueTokens(P
                                                  //| arser.java:292)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.parseObject(Parser.java:
                                                  //| 653)
                                                  //| 	at com.typesafe.config.impl.Parser$ParseContext.parseValue(Parser.j
                                                  //| Output exceeds cutoff limit.
  
  c.getObject("plugin.externalReport.reports").keySet()
  for {
    report <- c.getObject("plugin.externalReport.reports").keySet().asScala
    key    <- Set("title", "description", "rootPath", "reportName")
  } {
  
    println(c.getString(s"plugin.externalReport.reports.$report.$key"))
    
  }
  
}