package ws.nexus.jscompiler

import java.nio.charset.Charset
import java.io.File
import sbt._

object JSCompilerPlugin extends sbt.Plugin {
  import sbt.Keys._
  import JSCompilerKeys._

  val utf8 = Charset.forName("utf-8")

  object JSCompilerKeys {
    lazy val jscompile = TaskKey[Seq[File]]("jscompile", "Compiles coffee dust or less files.")
    lazy val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file IO. Defaults to utf-8.")
    lazy val filter = SettingKey[FileFilter]("filter", "Filter (default *.coffee, *.dust, *.less) for selecting files from default directories.")
  }



  private def compileSources( compiler: JSCompiler, charset: Charset, out: Logger )( pairSourceTarget:(File, File)) =
    try {
      val (sourceFile, targetFile) = pairSourceTarget
      out.info("Compiling %s" format sourceFile)
      compiler.compile( sourceFile, charset, out ).fold( sys.error, { compiled =>
        IO.write( targetFile, compiled )
        out.debug("Wrote to file %s" format targetFile)
        targetFile
      })
    } catch { case e: Exception =>
      throw new RuntimeException( "error occured while compiling %s: %s" format(pairSourceTarget._1, e.getMessage), e
      )
    }




  def jsCompilerSettingsIn(c: Configuration): Seq[Setting[_]] =
    inConfig(c)( jsCompilerSettings0 ++ Seq(
      sourceDirectory in jscompile <<= (sourceDirectory in c) { _ / "web" },
      //resourceManaged in jscompile <<= (resourceManaged in c) { _ / "toTargetFile" },
      cleanFiles in jscompile <<= (resourceManaged in jscompile)(_ :: Nil),
      watchSources in jscompile <<= sourceDirectory map  { base => ( base  ** "*.less" ).get ++ ( base  ** "*.dust" ).get ++ ( base  ** "*.coffee" ).get }
    )) ++ Seq(
      cleanFiles <++= (cleanFiles in jscompile in c),
      watchSources <++= (watchSources in jscompile in c),
      resourceGenerators in c <+= jscompile in c,
      compile in c <<= (compile in c).dependsOn(jscompile in c)
    )

  def jsCompilerSettings: Seq[Setting[_]] =
    jsCompilerSettingsIn(Compile) ++ jsCompilerSettingsIn(Test)

  def jsCompilerSettings0: Seq[Setting[_]] = Seq(
    charset in jscompile := Charset.forName("utf-8"),
    filter in jscompile := "*.less" || "*.dust" || "*.coffee",
    excludeFilter in jscompile := (".*"  - ".") || HiddenFileFilter,
//    clean in jscompile <<= jsCleanTask,
    jscompile <<= jsCompilerTask
  )

  private def jsCompilerTask =
    (streams, sourceDirectory in jscompile, resourceManaged in jscompile, filter in jscompile, excludeFilter in jscompile, charset in jscompile ) map {
    (out,     sourceDir,                    targetDir,                    incl,                excl,                       charset) =>
        compileChanged(sourceDir, targetDir, incl, excl, charset, out.log)
    }




  private def compileChanged(sourceDir: File, targetDir: File, incl: FileFilter, excl: FileFilter, charset: Charset, log: Logger) = {
    val jscompiler = new JSCompiler

    ( for( sourceFile <- sourceDir.descendentsExcept( incl, excl ).get;
           targetFile <- jscompiler.targetFile( sourceDir, sourceFile, targetDir ) if( sourceFile newerThan targetFile ) )
            yield (sourceFile, targetFile)
    ) match {
      case Nil =>
        log.debug("No file to compile")
        compiledFiles( targetDir )
      case pairSourceTarget =>
        log.info("Compiling %d file(s) to %s" format( pairSourceTarget.size, targetDir ) )
        pairSourceTarget map compileSources( jscompiler, charset, log )
        log.debug("Compiled %s file(s)" format pairSourceTarget.size)
        compiledFiles( targetDir )
    }
  }

  private def compiledFiles( under: File ) = ( under ** "*.css" ).get ++ ( under ** "*.js" ).get

}