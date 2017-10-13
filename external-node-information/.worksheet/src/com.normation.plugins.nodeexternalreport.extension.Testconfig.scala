package com.normation.plugins.nodeexternalreport.extension


import com.typesafe.config._
import scala.collection.JavaConverters.asScalaSetConverter


object Testconfig {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(214); 
  println("Welcome to the Scala worksheet");$skip(102); 
  
  val c = ConfigFactory.parseURL(this.getClass.getClassLoader.getResource("externalReports.conf"));System.out.println("""c  : com.typesafe.config.Config = """ + $show(c ));$skip(59); val res$0 = 
  
  c.getObject("plugin.externalReport.reports").keySet();System.out.println("""res0: java.util.Set[String] = """ + $show(res$0));$skip(233); 
  for {
    report <- c.getObject("plugin.externalReport.reports").keySet().asScala
    key    <- Set("title", "description", "rootPath", "reportName")
  } {
  
    println(c.getString(s"plugin.externalReport.reports.$report.$key"))}
    
  }
  
}
