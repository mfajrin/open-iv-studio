package com.example.core

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

data class OivPackageMetadata(
  val id: String,
  val version: String,
  val target: String,
  val name: String,
  val author: String,
  val description: String,
)

sealed interface OivOperation {
  data class AddFile(
    val sourceInZip: String,
    val destinationPath: String,
    val archivePath: String?,
  ) : OivOperation

  data class DeleteFile(
    val destinationPath: String,
    val archivePath: String?,
  ) : OivOperation

  data class CreateArchive(
    val archivePath: String,
  ) : OivOperation
}

data class OivManifest(
  val metadata: OivPackageMetadata,
  val operations: List<OivOperation>,
  val rawAssemblyXml: String,
  val totalArchiveEntries: Int,
)

object OivPackageParser {
  fun parseOivBytes(oivBytes: ByteArray): Result<OivManifest> {
    val entries =
      RpfAndArchiveParser.listZipEntries(oivBytes).getOrElse { err ->
        return Result.failure(IllegalArgumentException("File .oiv tidak valid (bukan arsip ZIP): ${err.message}"))
      }

    val assemblyEntry =
      entries.firstOrNull { !it.isDirectory && it.fullPath.equals("assembly.xml", ignoreCase = true) }
        ?: return Result.failure(IllegalArgumentException("Manifest assembly.xml tidak ditemukan di dalam paket .oiv"))

    val xmlBytes =
      RpfAndArchiveParser.extractZipEntry(oivBytes, assemblyEntry.fullPath).getOrElse { err ->
        return Result.failure(err)
      }
    val xmlText = String(xmlBytes, Charsets.UTF_8)

    return parseAssemblyXml(xmlText, entries.count { !it.isDirectory }).map { manifest ->
      manifest
    }
  }

  fun parseAssemblyXml(
    xmlText: String,
    totalArchiveEntries: Int = 0,
  ): Result<OivManifest> {
    return try {
      val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      }
      val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xmlText)))
      val root = doc.documentElement
      if (!root.tagName.equals("package", ignoreCase = true)) {
        return Result.failure(IllegalArgumentException("Root XML harus <package>, ditemukan <${root.tagName}>"))
      }

      val pkgId = root.getAttribute("id").ifBlank { "{unknown}" }
      val pkgTarget = root.getAttribute("target").ifBlank { "Five" }
      val pkgVersionAttr = root.getAttribute("version").ifBlank { "2.2" }

      val metaEl = firstChildElement(root, "metadata")
      val modName = metaEl?.let { childText(it, "name") }?.takeIf { it.isNotBlank() } ?: "Paket Mod OIV"
      val authorEl = metaEl?.let { firstChildElement(it, "author") }
      val author =
        authorEl?.let { childText(it, "displayName") }?.takeIf { it.isNotBlank() }
          ?: metaEl?.let { childText(it, "author") }?.takeIf { it.isNotBlank() }
          ?: "Anonim"
      val description =
        metaEl?.let { childText(it, "description") }?.takeIf { it.isNotBlank() }
          ?: "Tidak ada deskripsi paket."

      val versionEl = metaEl?.let { firstChildElement(it, "version") }
      val modVersion =
        if (versionEl != null) {
          val major = childText(versionEl, "major").ifBlank { "1" }
          val minor = childText(versionEl, "minor").ifBlank { "0" }
          val tag = childText(versionEl, "tag")
          if (tag.isNotBlank()) "$major.$minor ($tag)" else "$major.$minor"
        } else {
          pkgVersionAttr
        }

      val operations = mutableListOf<OivOperation>()
      val contentEl = firstChildElement(root, "content")
      if (contentEl != null) {
        collectOperations(contentEl, currentArchive = null, out = operations)
      }

      Result.success(
        OivManifest(
          metadata =
            OivPackageMetadata(
              id = pkgId,
              version = modVersion,
              target = pkgTarget,
              name = modName,
              author = author,
              description = description,
            ),
          operations = operations,
          rawAssemblyXml = xmlText,
          totalArchiveEntries = totalArchiveEntries,
        )
      )
    } catch (e: Exception) {
      Result.failure(IllegalArgumentException("Gagal mem-parsing assembly.xml: ${e.message}"))
    }
  }

  private fun collectOperations(
    parent: Element,
    currentArchive: String?,
    out: MutableList<OivOperation>,
  ) {
    val children = parent.childNodes
    for (i in 0 until children.length) {
      val node = children.item(i)
      if (node.nodeType != Node.ELEMENT_NODE) continue
      val el = node as Element
      when (el.tagName.lowercase()) {
        "archive" -> {
          val path = el.getAttribute("path").trim().replace('\\', '/').trim('/')
          val nestedArchive =
            if (currentArchive.isNullOrBlank()) path else "$currentArchive/$path"
          if (el.getAttribute("createIfNotExist").equals("true", ignoreCase = true) && nestedArchive.isNotBlank()) {
            out.add(OivOperation.CreateArchive(nestedArchive))
          }
          collectOperations(el, nestedArchive, out)
        }
        "add" -> {
          val src = el.getAttribute("source").trim().replace('\\', '/').trim('/')
          val dest = el.textContent.trim().replace('\\', '/').trim('/')
          if (src.isNotBlank() && dest.isNotBlank()) {
            out.add(OivOperation.AddFile(src, dest, currentArchive))
          }
        }
        "delete" -> {
          val dest = el.textContent.trim().replace('\\', '/').trim('/')
          if (dest.isNotBlank()) {
            out.add(OivOperation.DeleteFile(dest, currentArchive))
          }
        }
      }
    }
  }

  private fun firstChildElement(
    parent: Element,
    name: String,
  ): Element? {
    val list = parent.childNodes
    for (i in 0 until list.length) {
      val n = list.item(i)
      if (n.nodeType == Node.ELEMENT_NODE && (n as Element).tagName.equals(name, ignoreCase = true)) {
        return n
      }
    }
    return null
  }

  private fun childText(
    parent: Element,
    name: String,
  ): String {
    return firstChildElement(parent, name)?.textContent?.trim().orEmpty()
  }
}
