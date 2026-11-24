package com.example

import com.example.core.GtaHashUtils
import com.example.core.RpfAndArchiveParser
import com.example.core.RpfEncryptionType
import com.example.core.XmlTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVCoreUnitTest {
  @Test
  fun joaatHash_matchesKnownGtaVModels() {
    val adder = GtaHashUtils.joaat("adder")
    assertEquals("0xB779A091", adder.hexUpper)

    val caseInsensitive = GtaHashUtils.joaat("ADDER")
    assertEquals(adder.unsignedInt, caseInsensitive.unsignedInt)
  }

  @Test
  fun rpf7_buildAndParseOpenArchive_roundTripsEntries() {
    val xmlPayload = "<Test><Value value=\"42\" /></Test>".toByteArray(Charsets.UTF_8)
    val txtPayload = "hello rage".toByteArray(Charsets.UTF_8)
    val rpfBytes =
      RpfAndArchiveParser.buildOpenRpf7(
        mapOf(
          "config.xml" to xmlPayload,
          "readme.txt" to txtPayload,
        )
      )

    assertTrue(RpfAndArchiveParser.isRpf7(rpfBytes))
    val parsed = RpfAndArchiveParser.parseRpf7(rpfBytes).getOrThrow()
    assertEquals(RpfEncryptionType.OPEN, parsed.header.encryption)
    assertEquals(2, parsed.entries.size)

    val cfgEntry = parsed.entries.first { it.name == "config.xml" }
    val extracted = RpfAndArchiveParser.extractRpf7Entry(rpfBytes, cfgEntry).getOrThrow()
    assertEquals(String(xmlPayload, Charsets.UTF_8), String(extracted, Charsets.UTF_8))
  }

  @Test
  fun rpf7_hierarchicalBuildAndExtractAll_roundTripsNestedFoldersAndHex() {
    val handlingBytes = "<CHandlingDataMgr />".toByteArray(Charsets.UTF_8)
    val binaryBytes = byteArrayOf(0x52, 0x50, 0x46, 0x37, 0x01, 0x02, 0x7F.toByte(), 0x00)
    val rpfBytes =
      RpfAndArchiveParser.buildHierarchicalOpenRpf7(
        files =
          mapOf(
            "common/data/handling.meta" to handlingBytes,
            "x64/textures/livery.bin" to binaryBytes,
          ),
        emptyDirs = setOf("empty_dir", "common/empty_sub"),
      )

    val ws = RpfAndArchiveParser.extractAllOpenRpf7(rpfBytes).getOrThrow()
    assertEquals(RpfEncryptionType.OPEN, ws.header.encryption)
    assertTrue(ws.files.containsKey("common/data/handling.meta"))
    assertTrue(ws.files.containsKey("x64/textures/livery.bin"))
    assertTrue(ws.directories.contains("empty_dir"))
    assertTrue(ws.directories.contains("common/empty_sub"))
    assertEquals(
      String(handlingBytes, Charsets.UTF_8),
      String(ws.files.getValue("common/data/handling.meta"), Charsets.UTF_8),
    )

    val hex = RpfAndArchiveParser.formatHexDump(binaryBytes, 64)
    assertTrue(hex.contains("52 50 46 37"))
    assertTrue(hex.contains("RPF7"))
  }

  @Test
  fun xmlTools_validatesFormatsAndParsesTree() {
    val raw = "<Root><Item type=\"CHandlingData\"><fMass value=\"1500\"/></Item></Root>"
    val valid = XmlTools.validateXml(raw)
    assertTrue(valid.isValid)
    assertEquals("Root", valid.rootTag)

    val formatted = XmlTools.formatXml(raw).getOrThrow()
    assertTrue(formatted.contains("\n"))

    val tree = XmlTools.parseXmlTree(formatted).getOrThrow()
    assertEquals("Root", tree.tagName)
    assertEquals(1, tree.children.size)
    assertEquals("CHandlingData", tree.children[0].attributes.first().second)

    val broken = XmlTools.validateXml("<Root><Unclosed></Root>")
    assertFalse(broken.isValid)
  }

  @Test
  fun oivPackageParser_parsesAssemblyManifestAndOperations() {
    val assemblyXml =
      """<?xml version="1.0" encoding="UTF-8"?>
<package version="2.2" id="{11112222-3333-4444-5555-666677778888}" target="Five">
  <metadata>
    <name>Test Livery Mod</name>
    <version><major>2</major><minor>1</minor></version>
    <author><displayName>ModderID</displayName></author>
    <description>Mod uji coba</description>
  </metadata>
  <content>
    <archive path="update/update.rpf" createIfNotExist="True" type="RPF7">
      <add source="content/handling.meta">common/data/handling.meta</add>
      <delete>common/data/old.meta</delete>
    </archive>
  </content>
</package>"""
    val baos = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(baos).use { zos ->
      zos.putNextEntry(java.util.zip.ZipEntry("assembly.xml"))
      zos.write(assemblyXml.toByteArray(Charsets.UTF_8))
      zos.closeEntry()
      zos.putNextEntry(java.util.zip.ZipEntry("content/handling.meta"))
      zos.write("<CHandlingDataMgr />".toByteArray(Charsets.UTF_8))
      zos.closeEntry()
    }

    val manifest = com.example.core.OivPackageParser.parseOivBytes(baos.toByteArray()).getOrThrow()
    assertEquals("Test Livery Mod", manifest.metadata.name)
    assertEquals("2.1", manifest.metadata.version)
    assertEquals("ModderID", manifest.metadata.author)
    assertEquals(3, manifest.operations.size)
  }
}
