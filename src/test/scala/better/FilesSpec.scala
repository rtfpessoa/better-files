package better

import better.files._, Cmds._, Closeable._

import org.scalatest._

class FilesSpec extends FlatSpec with BeforeAndAfterEach with Matchers {
  var testRoot: File = _    //TODO: Get rid of mutable test vars
  var fa: File = _
  var a1: File = _
  var a2: File = _
  var t1: File = _
  var t2: File = _
  var fb: File = _
  var b1: File = _
  var b2: File = _

  /**
   * Setup the following directory structure under root
   * /a
   *  /a1
   *  /a2
   *    a21.txt
   *    a22.txt
   * /b
   *    b1/ --> ../a1
   *    b2.txt --> ../a2/a22.txt
   */

  override def beforeEach() = {
    testRoot = File.newTempDir("better-files")
    fa = testRoot/"a"
    a1 = testRoot/"a"/"a1"
    a2 = testRoot/"a"/"a2"
    t1 = testRoot/"a"/"a1"/"t1.txt"
    t2 = testRoot/"a"/"a1"/"t2.txt"
    fb = testRoot/"b"
    b1 = testRoot/"b"/"b1"
    b2 = testRoot/"b"/"b2.txt"
    Seq(a1, a2, fb) foreach mkdirs
    Seq(t1, t2) foreach touch
  }

  override def afterEach() = rm(testRoot)

  "files" can "be instantiated" in {
    val f = File("/User/johndoe/Documents")
    val f1: File = file"/User/johndoe/Documents"
    val f2: File = root/"User"/"johndoe"/"Documents"
    val f3: File = home/"Documents"
    val f4: File = new java.io.File("/User/johndoe/Documents").toScala
    val f5: File = "/User"/"johndoe"/"Documents"
    val f6: File = "/User/johndoe/Documents".toFile
    val f7: File = root/"User"/"johndoe"/"Documents"/"presentations" / `..`

    root.toString shouldEqual "file:///"
    home.toString.count(_ == '/') should be > 1
    (root/"usr"/"johndoe"/"docs").toString shouldEqual "file:///usr/johndoe/docs"
    Seq(f, f1, f2, f4, f5, f6, f7).map(_.toString).toSet shouldBe Set(f.toString)
  }

  it can "be matched" in {
    "src"/"test"/"foo" match {
      case SymbolicLink(to) => fail()   //this must be first case statement if you want to handle symlinks specially; else will follow link
      case Directory(children) => fail()
      case RegularFile(contents) => fail()
      case other if other.exists => fail()  //A file may not be one of the above e.g. UNIX pipes, sockets, devices etc
      case _ =>                               //A file that does not exist
    }
    root/"dev"/"null" match {
      case SymbolicLink(to) => fail()
      case Directory(children) => fail()
      case RegularFile(contents) => fail()
      case other if other.exists =>   //A file can be not any of the above e.g. UNIX pipes & sockets etc
      case _ => fail()
    }
    root/"dev" match {
      case Directory(children) => children.exists(_.fullPath == "/dev/null") shouldBe true // /dev should have 'null'
      case _ => fail()
    }
  }

  it should "do basic I/O" in {
    import scala.language.postfixOps
    t1 < "hello"
    t1.contentAsString shouldEqual "hello"
    t1.appendNewLine << "world"
    (t1!) shouldEqual "hello\nworld\n"
    t1.chars.toStream should contain theSameElementsInOrderAs "hello\nworld\n".toSeq
    //t1.contentType shouldBe Some("txt")
    "foo" `>:` t1
    "bar" >>: t1
    t1.contentAsString shouldEqual "foobar\n"
    t1.appendLines("hello", "world")
    t1.contentAsString shouldEqual "foobar\nhello\nworld\n"
    t2.write("hello").append("world").contentAsString shouldEqual "helloworld"

    (testRoot/"diary")
      .createIfNotExists()
      .appendNewLine()
      .appendLines("My name is", "Inigo Montoya")
      .lines.toSeq should contain theSameElementsInOrderAs Seq("", "My name is", "Inigo Montoya")
  }

  it should "glob" in {
    ("src"/"test").glob("**/*.scala").map(_.name).toSeq shouldEqual Seq("FilesSpec.scala")
    ("src"/"test").listRecursively().filter(_.extension == Some(".scala")) should have length 1
    ls("src"/"test") should have length 1
    ("src"/"test").listRecursively(maxDepth = 1) should have length 2
    ls_r("src"/"test") should have length 4
  }

  it should "support names/extensions" in {
    fa.extension shouldBe None
    fa.nameWithoutExtension shouldBe fa.name
    t1.extension shouldBe Some(".txt")
    t1.name shouldBe "t1.txt"
    t1.nameWithoutExtension shouldBe "t1"
    t1.changeExtensionTo(".md").name shouldBe "t1.md"
    //t1.contentType shouldBe Some("txt")
    //("src" / "test").toString shouldNot be
  }

  it must "have .size" in {
    t1.size shouldBe 0
    t1.write("Hello World")
    t1.size should be > 0L
    testRoot.size should be > (t1.size + t2.size)
  }

  it should "set/unset permissions" in {
    import java.nio.file.attribute.PosixFilePermission
    t1.permissions(PosixFilePermission.OWNER_EXECUTE) shouldBe false

    chmod_+(PosixFilePermission.OWNER_EXECUTE, t1)
    t1(PosixFilePermission.OWNER_EXECUTE) shouldBe true

    chmod_-(PosixFilePermission.OWNER_EXECUTE, t1)
    t1.isOwnerExecutable shouldBe false
  }

  it should "support equality" in {
    fa shouldEqual (testRoot/"a")
    fa shouldNot equal (testRoot/"b")
    //val c1 = fa.checksum()
    //fa.checksum() shouldEqual c1
    t1 < "hello"
    t2 < "hello"
    (t1 == t2) shouldBe false
    (t1 === t2) shouldBe true
    t2 < "hello world"
    (t1 == t2) shouldBe false
    (t1 === t2) shouldBe false
    //fa.checksum() should not equal c1
  }

  it should "support chown/chgrp" in {
    fa.owner.getName should not be empty
    fa.group.getName should not be empty
    //fa.chown("nobody").chgrp("nobody")
  }

  it should "support ln/cp/mv" in {
    val magicWord = "Hello World"
    t1 write magicWord
    // link
    b1.linkTo(a1, symbolic = true)
    ln_s(b2, t2)
    (b1 / "t1.txt").contentAsString shouldEqual magicWord
    // copy
    b2.contentAsString shouldBe empty
    t1.checksum() should not equal t2.checksum()
    a[java.nio.file.FileAlreadyExistsException] should be thrownBy (t1 copyTo t2)
    t1.copyTo(t2, overwrite = true)
    t1.exists shouldBe true
    t1.checksum() shouldEqual t2.checksum()
    b2.contentAsString shouldEqual magicWord
    // rename
    t2.name shouldBe "t2.txt"
    t2.exists shouldBe true
    val t3 = t2 renameTo "t3.txt"
    t3.name shouldBe "t3.txt"
    t2.exists shouldBe false
    t3.exists shouldBe true
    // move
    t3 moveTo t2
    t2.exists shouldBe true
    t3.exists shouldBe false
  }

  it should "support custom codec" in {
    import scala.io.Codec
    t1.write("你好世界")(codec = "UTF8")
    t1.contentAsString(Codec.ISO8859) should not equal "你好世界"
    t1.contentAsString(Codec.UTF8) shouldEqual "你好世界"
    val c1 = t1.md5
    val c2 = t1.overwrite("你好世界")(Codec.ISO8859).md5
    val c3 = t1.checksum()
    c2 shouldEqual c3
    c1 should not equal c3
  }

  it should "copy" in {
    (fb / "t3" / "t4.txt").createIfNotExists().write("Hello World")
    cp(fb / "t3", fb / "t5")
    (fb / "t5" / "t4.txt").contentAsString shouldEqual "Hello World"
    (fb / "t3").exists shouldBe true
  }

  it should "move" in {
    (fb / "t3" / "t4.txt").createIfNotExists().write("Hello World")
    mv(fb / "t3", fb / "t5")
    (fb / "t5" / "t4.txt").contentAsString shouldEqual "Hello World"
    (fb / "t3").notExists shouldBe true
  }

  it should "touch" in {
    (fb / "z1").exists shouldBe false
    (fb / "z1").isEmpty shouldBe true
    (fb / "z1").touch()
    (fb / "z1").exists shouldBe true
    (fb / "z1").isEmpty shouldBe true
    Thread.sleep(1000)
    (fb / "z1").lastModifiedTime.getEpochSecond should be < (fb / "z1").touch().lastModifiedTime.getEpochSecond
  }

  it should "support file in/out" in {
    t1 < "hello world"
    t1.in > t2.out
    t2.contentAsString shouldEqual "hello world"
  }

  it should "zip/unzip directories" in {
    t1.write("hello world")
    val zipFile = testRoot.zip()
    zipFile.size should be > 100L
    zipFile.name should endWith (".zip")
    val destination = zipFile.unzip()
    (destination/"a"/"a1"/"t1.txt").contentAsString shouldEqual "hello world"
  }

  it should "zip/unzip single files" in {
    t1.write("hello world")
    val zipFile = t1.zip()
    zipFile.size should be > 100L
    zipFile.name should endWith (".zip")
    val destination = zipFile.unzip()
    (destination/"t1.txt").contentAsString shouldEqual "hello world"
  }

  it should "gzip" in {
    for {
      writer <- managed((testRoot / "test.gz").out.buffered.gzipped.writer)
    } writer.write("Hello world")

    (testRoot / "test.gz").in.gzipped.lines.toSeq shouldEqual Seq("Hello world")
  }
  //TODO: Test above for all kinds of FileType
}
