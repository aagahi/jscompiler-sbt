package ws.nexus.jscompiler

import java.io.File
import java.net.URL
import java.nio.charset.Charset
import org.mozilla.javascript.tools.shell.Global
import org.mozilla.javascript.{ScriptableObject, Scriptable, JavaScriptException, Context}
import sbt.{IO, Logger}
import collection.mutable.{SynchronizedMap, HashMap}


object JSCompiler {
  private val jscache = new HashMap[String, String] with SynchronizedMap[String, String]
}

class JSCompiler {
  import JSCompiler._

  private def compile( prejs:String, js:String, callExpression:String, fileType:String )( source:String ) = {
    val ctx = Context.enter()
    ctx.setOptimizationLevel(-1)
    try {
      val global = new Global()
      global.init(ctx)

      val scope = ctx.initStandardObjects(global)
      ctx.evaluateString( scope, prejs, fileType, 1, null )
      ctx.evaluateString( scope, js, fileType, 1, null)
      scope.put("scriptSource", scope, source );
      ctx.evaluateString( scope, callExpression, "JSCompiler", 0, null).toString

    }
    finally {
      Context.exit()
    }
  }

  private def fetchURL( url:URL, charset:Charset ) = {
    jscache.getOrElseUpdate( url.toString, {
      io.Source.fromURL( url )(io.Codec(charset)).mkString
    })
  }
  private def fetchResource( resource:String, charset:Charset ) = {
    jscache.getOrElseUpdate( resource, {
      val is = getClass().getResourceAsStream( resource )
      io.Source.fromInputStream( is )(io.Codec(charset)).mkString
    })

  }

  private def lessCompiler( sourceFile:File ) = {
    val prejs = """
arguments = ['"""+sourceFile.getAbsolutePath+"""'];
print = function() {};
quit = function() {};
writeFile = function() {};


var compileLess = function(input) {
  var parser = new less.Parser();
  var result;
  parser.parse(input, function (e, root) {
    if (e) throw e;
    result = root.toCSS();
  });
  return result;
};
"""
    val url = new URL( "https://raw.github.com/cloudhead/less.js/master/dist/less-rhino-1.1.5.js")
    val js = fetchURL( url, Charset.forName("utf-8") )
    //val js = fetchResource("/less-rhino-1.2.1.js", Charset.forName("utf-8") )
    
    compile( prejs, js, "compileLess(scriptSource);", "less 1.2.1" ) _
  }


  private def dustCompiler() = {
    val prejs = """
var window = {};
window.dust = {};
"""
    val url = new URL( "https://raw.github.com/akdubya/dustjs/master/dist/dust-full-0.3.0.js")
    val js = fetchURL( url, Charset.forName("utf-8") )

    compile( prejs, js, "window.dust.compile(scriptSource);", "dust 0.3.0" ) _
  }

  private def coffeeCompiler() = {
    val url = new URL( "https://raw.github.com/jashkenas/coffee-script/master/extras/coffee-script.js" )
    val js = fetchURL( url, Charset.forName("utf-8") )

    compile( "", js, "CoffeeScript.compile(scriptSource);", "coffee last" ) _
  }


  @inline private def fileType( sourceFile:File ) = {
    val name = sourceFile.getName
    val index = name.lastIndexOf(".")
    if( index > 0 )  Some( name.substring( index + 1 ).toLowerCase )
    else None
  }

  private def fileCompiler( sourceFile:File, charset:Charset ) = {
     fileType( sourceFile ) match {
      case Some( "less" ) => lessCompiler( sourceFile )
      case Some( "dust" ) => dustCompiler()
      case Some( "coffee" ) => coffeeCompiler()
      case _ => { s:String => s }
    }
  }

  def targetFile( sourceDir: File, sourceFile: File, targetDir: File) = {
    val ( from, to ) = fileType( sourceFile ) match {
      case Some( "less" ) => (".less", ".css" )
      case Some( "dust" ) => (".dust", ".js" )
      case Some( "coffee" ) => (".coffee", ".js" )
      case _ => ( "", "" )
    }
    Some( new File( targetDir, IO.relativize( sourceDir, sourceFile ).get.replace( from, to ) ) )
  }

  def compile( sourceFile:File, charset:Charset, out: Logger ): Either[String, String] = {
    try {
      val content = io.Source.fromFile(sourceFile)(io.Codec(charset)).mkString
      Right( fileCompiler( sourceFile, charset )( content ) )
    } catch {
      case e : JavaScriptException =>
        e.getValue match {
          case v: Scriptable =>
            Left(ScriptableObject.getProperty(v, "message").toString)
          case v => sys.error("unknown exception value type %s" format v)
        }
    }

  }




}
