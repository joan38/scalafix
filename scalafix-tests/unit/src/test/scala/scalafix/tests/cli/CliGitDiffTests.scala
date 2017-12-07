package scalafix.tests.cli

import java.lang.ProcessBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, Path, StandardOpenOption}

import org.scalactic.source.Position
import org.scalatest._
import org.scalatest.FunSuite

import scala.collection.immutable.Seq
import scala.util._
import scalafix.cli
import scalafix.internal.cli.CommonOptions
import scalafix.testkit.DiffAssertions

class CliGitDiffTests() extends FunSuite with DiffAssertions {
  gitTest("addition") { (fs, git, cli) =>
    val oldCode = "old.scala"
    val newCode = "new.scala"
    val newCodeAbsPath = fs.absPath(newCode)

    fs.add(
      oldCode,
      """|object OldCode {
         |  // This is old code, where var's blossom
         |  var oldVar = 1
         |}""".stripMargin)
    git.add(oldCode)
    addConf(fs, git)
    git.commit()

    git.checkout("pr-1")

    fs.add(
      newCode,
      """|object NewCode {
         |  // New code, no vars
         |  var newVar = 1
         |}""".stripMargin)
    git.add(newCode)
    git.commit()

    val obtained = runDiff(cli)

    val expected =
      s"""|Running DisableSyntax
          |$newCodeAbsPath:3:3: error: [DisableSyntax.keywords.var] keywords.var is disabled
          |  var newVar = 1
          |  ^
          |""".stripMargin

    assertNoDiff(obtained, expected)
  }

  gitTest("modification") { (fs, git, cli) =>
    val oldCode = "old.scala"
    val oldCodeAbsPath = fs.absPath(oldCode)

    fs.add(
      oldCode,
      """|object OldCode {
         |  // This is old code, where var's blossom
         |  var oldVar = 1
         |}""".stripMargin)
    git.add(oldCode)
    addConf(fs, git)
    git.commit()

    git.checkout("pr-1")
    fs.replace(
      oldCode,
      """|object OldCode {
         |  // This is old code, where var's blossom
         |  var oldVar = 1
         |}
         |object NewCode {
         |  // It's not ok to add new vars
         |  var newVar = 2
         |}""".stripMargin
    )
    git.add(oldCode)
    git.commit()

    val obtained = runDiff(cli)

    val expected =
      s"""|Running DisableSyntax
          |$oldCodeAbsPath:7:3: error: [DisableSyntax.keywords.var] keywords.var is disabled
          |  var newVar = 2
          |  ^
          |""".stripMargin

    assertNoDiff(obtained, expected)
  }

  gitTest("rename") { (fs, git, cli) =>
    val oldCode = "old.scala"
    val newCode = "new.scala"
    val newCodeAbsPath = fs.absPath(newCode)

    fs.add(
      oldCode,
      """|object OldCode {
         |  // This is old code, where var's blossom
         |  var oldVar = 1
         |}""".stripMargin)
    git.add(oldCode)
    addConf(fs, git)
    git.commit()

    git.checkout("pr-1")
    fs.replace(
      oldCode,
      """|object OldCode {
         |  // This is old code, where var's blossom
         |  var oldVar = 1
         |  // It's not ok to add new vars
         |  var newVar = 2
         |}""".stripMargin
    )
    fs.mv(oldCode, newCode)
    git.add(oldCode)
    git.add(newCode)
    git.commit()

    val obtained = runDiff(cli)

    val expected =
      s"""|Running DisableSyntax
          |$newCodeAbsPath:5:3: error: [DisableSyntax.keywords.var] keywords.var is disabled
          |  var newVar = 2
          |  ^
          |""".stripMargin

    assertNoDiff(obtained, expected)
  }

  test("not a git repo") {
    val fs = new Fs()
    addConf(fs)
    val cli = new Cli(fs.workingDirectory)
    val obtained = runDiff(cli)
    val expected =
      s"error: ${fs.workingDirectory} is not a git repository"

    assert(obtained.startsWith(expected))
  }

  private def runDiff(cli: Cli, args: String*): String =
    noColor(cli.run("--non-interactive" :: "--diff" :: args.toList))

  private val confFile = ".scalafix.conf"
  private def addConf(fs: Fs, git: Git): Unit = {
    addConf(fs)
    git.add(confFile)
  }

  private def addConf(fs: Fs): Unit = {
    fs.add(
      confFile,
      """|rules = DisableSyntax
         |DisableSyntax.keywords = [var]""".stripMargin)
  }

  private def noColor(in: String): String =
    in.replaceAll("\u001B\\[[;\\d]*m", "")

  private def gitTest(name: String)(body: (Fs, Git, Cli) => Unit): Unit = {
    test(name) {
      val fs = new Fs()
      val git = new Git(fs.workingDirectory)
      val cli = new Cli(fs.workingDirectory)

      body(fs, git, cli)
    }
  }

  private class Fs() {
    val workingDirectory: Path =
      Files.createTempDirectory("scalafix")

    workingDirectory.toFile.deleteOnExit()

    def add(filename: String, content: String): Unit =
      write(filename, content, StandardOpenOption.CREATE_NEW)

    def append(filename: String, content: String): Unit =
      write(filename, content, StandardOpenOption.APPEND)

    def replace(filename: String, content: String): Unit = {
      rm(filename)
      add(filename, content)
    }

    def rm(filename: String): Unit =
      Files.delete(path(filename))

    def mv(src: String, dst: String): Unit =
      Files.move(path(src), path(dst))

    def absPath(filename: String): String =
      path(filename).toAbsolutePath.toString

    private def write(
        filename: String,
        content: String,
        op: StandardOpenOption): Unit = {
      Files.write(path(filename), content.getBytes, op)
    }

    private def path(filename: String): Path =
      workingDirectory.resolve(filename)
  }

  private class Git(workingDirectory: Path) {
    import org.eclipse.jgit.api.{Git => JGit}

    private val git = JGit.init().setDirectory(workingDirectory.toFile).call()
    private var revision = 0

    def add(filename: String): Unit =
      git.add().addFilepattern(filename).call()

    def rm(filename: String): Unit =
      git.rm().addFilepattern(filename).call()

    def checkout(branch: String): Unit =
      git.checkout().setCreateBranch(true).setName(branch).call()

    def tag(name: String, message: String): Unit =
      git.tag().setName(name).setMessage(message).call()

    def commit(): Unit = {
      git.commit().setMessage(s"r$revision").call()
      revision += 1
    }
  }

  private class Cli(workingDirectory: Path) {
    def run(args: List[String]): String = {
      val baos = new ByteArrayOutputStream()
      val ps = new PrintStream(baos)
      val exit = cli.Cli.runMain(
        args.to[Seq],
        CommonOptions(
          workingDirectory = workingDirectory.toAbsolutePath.toString,
          out = ps,
          err = ps
        )
      )
      val output = new String(baos.toByteArray(), StandardCharsets.UTF_8)
      output
    }
  }
}