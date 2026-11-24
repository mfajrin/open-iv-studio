package com.example.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream

enum class RpfEncryptionType(val label: String) {
  OPEN("OPEN (Tidak Terenkripsi)"),
  AES("AES-256"),
  NG("Rockstar NG (Encrypted)"),
  UNKNOWN("Kunci Kustom / Tidak Dikenal"),
}

data class RpfHeaderInfo(
  val magic: String,
  val entryCount: Int,
  val namesLength: Int,
  val encryption: RpfEncryptionType,
)

data class ArchiveEntryInfo(
  val fullPath: String,
  val name: String,
  val isDirectory: Boolean,
  val size: Long,
  val compressedSize: Long = size,
  val offset: Long = 0L,
  val isResource: Boolean = false,
)

data class RpfParseResult(
  val header: RpfHeaderInfo,
  val entries: List<ArchiveEntryInfo>,
  val diagnosticMessage: String,
)

object RpfAndArchiveParser {
  private const val RPF7_MAGIC = 0x52504637 // "RPF7" in big-endian / 0x37465052 LE
  private const val RPF7_MAGIC_LE = 0x37465052 // 'R','P','F','7'
  private const val ENC_OPEN = 0x4E45504F // "OPEN"
  private const val ENC_AES = 0x0FFFFFF9
  private const val ENC_NG = 0x0FEFFFFF

  fun isRpf7(bytes: ByteArray): Boolean {
    if (bytes.size < 16) return false
    val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
    return magic == RPF7_MAGIC_LE || magic == RPF7_MAGIC
  }

  fun parseRpf7(bytes: ByteArray): Result<RpfParseResult> {
    if (bytes.size < 16) {
      return Result.failure(IllegalArgumentException("File RPF terlalu kecil (< 16 byte)"))
    }
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magicInt = buf.int
    val magicStr =
      String(bytes.copyOfRange(0, 4), Charsets.US_ASCII)
    if (magicInt != RPF7_MAGIC_LE && magicInt != RPF7_MAGIC) {
      return Result.failure(IllegalArgumentException("Bukan header RPF7 yang valid (magic: $magicStr)"))
    }
    val entryCount = buf.int
    val namesLength = buf.int
    val encRaw = buf.int
    val encType =
      when (encRaw) {
        ENC_OPEN -> RpfEncryptionType.OPEN
        ENC_AES -> RpfEncryptionType.AES
        ENC_NG -> RpfEncryptionType.NG
        else -> RpfEncryptionType.UNKNOWN
      }
    val header =
      RpfHeaderInfo(
        magic = magicStr,
        entryCount = entryCount,
        namesLength = namesLength,
        encryption = encType,
      )

    if (encType != RpfEncryptionType.OPEN) {
      return Result.success(
        RpfParseResult(
          header = header,
          entries = emptyList(),
          diagnosticMessage =
            "Arsip RPF7 terdeteksi ($entryCount entri, enkripsi ${encType.label}). " +
              "Arsip terenkripsi NG/AES dari instalasi PC memerlukan kunci dekripsi lokal; gunakan arsip OPEN atau paket .oiv/.zip untuk ekstraksi langsung.",
        )
      )
    }

    val tocStart = 16
    val tocSize = entryCount * 16
    val namesStart = tocStart + tocSize
    if (entryCount < 0 || namesLength < 0 || namesStart + namesLength > bytes.size) {
      return Result.failure(IllegalArgumentException("Struktur TOC RPF7 rusak atau terpotong"))
    }

    data class RawTocEntry(
      val nameOffset: Int,
      val isDir: Boolean,
      val dirStartIdx: Int,
      val dirCount: Int,
      val fileSize: Long,
      val fileOffset: Long,
      val compressedSize: Long,
      val isResource: Boolean,
    )

    val rawEntries = ArrayList<RawTocEntry>(entryCount)
    for (i in 0 until entryCount) {
      val off = tocStart + i * 16
      buf.position(off)
      val w0 = buf.int.toLong() and 0xFFFFFFFFL
      val w1 = buf.int.toLong() and 0xFFFFFFFFL
      val w2 = buf.int.toLong() and 0xFFFFFFFFL
      val w3 = buf.int.toLong() and 0xFFFFFFFFL

      // Directory marker in RPF7: w1 == 0x7FFFFF00
      if (w1 == 0x7FFFFF00L) {
        rawEntries.add(
          RawTocEntry(
            nameOffset = (w0 and 0xFFFFL).toInt(),
            isDir = true,
            dirStartIdx = w2.toInt(),
            dirCount = w3.toInt(),
            fileSize = 0L,
            fileOffset = 0L,
            compressedSize = 0L,
            isResource = false,
          )
        )
      } else {
        // Binary/Resource entry
        val nameOff = (w0 and 0xFFFFL).toInt()
        val compSize = ((w0 ushr 16) and 0xFFFFL) or ((w1 and 0xFFL) shl 16)
        val fileOffsetBlocks = (w1 ushr 8) and 0xFFFFFFL
        val realOffset = fileOffsetBlocks * 512L
        val isRes = (w3 and 0x80000000L) != 0L
        val uncompSize = if (isRes) compSize else w2
        rawEntries.add(
          RawTocEntry(
            nameOffset = nameOff,
            isDir = false,
            dirStartIdx = 0,
            dirCount = 0,
            fileSize = if (uncompSize > 0) uncompSize else compSize,
            fileOffset = realOffset,
            compressedSize = compSize,
            isResource = isRes,
          )
        )
      }
    }

    fun readName(nameOffset: Int): String {
      val abs = namesStart + nameOffset
      if (abs < namesStart || abs >= namesStart + namesLength) return "entry_$nameOffset"
      var end = abs
      while (end < namesStart + namesLength && bytes[end] != 0.toByte()) end++
      return String(bytes, abs, end - abs, Charsets.UTF_8).ifEmpty { "root" }
    }

    val collected = mutableListOf<ArchiveEntryInfo>()
    fun walk(
      index: Int,
      parentPath: String,
    ) {
      if (index !in rawEntries.indices) return
      val e = rawEntries[index]
      val name = if (index == 0) "" else readName(e.nameOffset)
      val fullPath =
        when {
          index == 0 -> ""
          parentPath.isEmpty() -> name
          else -> "$parentPath/$name"
        }
      if (index != 0) {
        collected.add(
          ArchiveEntryInfo(
            fullPath = fullPath,
            name = name,
            isDirectory = e.isDir,
            size = e.fileSize,
            compressedSize = e.compressedSize,
            offset = e.fileOffset,
            isResource = e.isResource,
          )
        )
      }
      if (e.isDir) {
        for (childIdx in e.dirStartIdx until (e.dirStartIdx + e.dirCount)) {
          walk(childIdx, fullPath)
        }
      }
    }
    if (rawEntries.isNotEmpty()) {
      walk(0, "")
    }

    return Result.success(
      RpfParseResult(
        header = header,
        entries = collected,
        diagnosticMessage = "RPF7 OPEN dimuat • ${collected.size} entri siap dieksplorasi",
      )
    )
  }

  fun extractRpf7Entry(
    rpfBytes: ByteArray,
    entry: ArchiveEntryInfo,
  ): Result<ByteArray> {
    if (entry.isDirectory) return Result.failure(IllegalArgumentException("Entri adalah direktori"))
    val start = entry.offset.toInt()
    val readLen = (if (entry.compressedSize > 0) entry.compressedSize else entry.size).toInt()
    if (start < 0 || start + readLen > rpfBytes.size) {
      return Result.failure(IllegalArgumentException("Offset RPF7 di luar batas file"))
    }
    val slice = rpfBytes.copyOfRange(start, start + readLen)
    // Check if zlib/deflate compressed (common in RPF7 binary entries when compressedSize != size)
    if (entry.compressedSize > 0 && entry.compressedSize != entry.size && !entry.isResource) {
      return try {
        val inflater = InflaterInputStream(ByteArrayInputStream(slice))
        Result.success(inflater.readBytes())
      } catch (_: Exception) {
        Result.success(slice)
      }
    }
    return Result.success(slice)
  }

data class ExtractedRpf7Workspace(
  val header: RpfHeaderInfo,
  val files: Map<String, ByteArray>,
  val directories: Set<String>,
  val entryMetadata: Map<String, ArchiveEntryInfo>,
  val diagnosticMessage: String,
)

  fun extractAllOpenRpf7(rpfBytes: ByteArray): Result<ExtractedRpf7Workspace> {
    val parsed = parseRpf7(rpfBytes).getOrElse { return Result.failure(it) }
    val metaMap = parsed.entries.associateBy { it.fullPath }
    if (parsed.header.encryption != RpfEncryptionType.OPEN) {
      return Result.success(
        ExtractedRpf7Workspace(
          header = parsed.header,
          files = emptyMap(),
          directories = emptySet(),
          entryMetadata = metaMap,
          diagnosticMessage = parsed.diagnosticMessage,
        )
      )
    }

    val dirs = linkedSetOf<String>()
    val files = linkedMapOf<String, ByteArray>()
    for (entry in parsed.entries) {
      if (entry.isDirectory) {
        dirs.add(entry.fullPath)
      } else {
        val data = extractRpf7Entry(rpfBytes, entry).getOrElse { return Result.failure(it) }
        files[entry.fullPath] = data
        val parent = entry.fullPath.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
          val parts = parent.split('/')
          for (i in 1..parts.size) {
            dirs.add(parts.subList(0, i).joinToString("/"))
          }
        }
      }
    }
    return Result.success(
      ExtractedRpf7Workspace(
        header = parsed.header,
        files = files,
        directories = dirs,
        entryMetadata = metaMap,
        diagnosticMessage = parsed.diagnosticMessage,
      )
    )
  }

  /**
   * Builds a genuine unencrypted RPF7 OPEN archive binary supporting nested directories.
   */
  fun buildOpenRpf7(files: Map<String, ByteArray>): ByteArray =
    buildHierarchicalOpenRpf7(files = files, emptyDirs = emptySet())

  fun buildHierarchicalOpenRpf7(
    files: Map<String, ByteArray>,
    emptyDirs: Set<String> = emptySet(),
  ): ByteArray {
    class DirNode(
      val name: String,
      val fullPath: String,
    ) {
      val subDirs = linkedMapOf<String, DirNode>()
      val leafFiles = linkedMapOf<String, ByteArray>()
    }

    val root = DirNode(name = "", fullPath = "")

    fun ensureDir(path: String): DirNode {
      val parts = path.trim('/').replace('\\', '/').split('/').filter { it.isNotBlank() && it != ".." }
      var cur = root
      var acc = ""
      for (part in parts) {
        acc = if (acc.isEmpty()) part else "$acc/$part"
        cur = cur.subDirs.getOrPut(part) { DirNode(name = part, fullPath = acc) }
      }
      return cur
    }

    emptyDirs.forEach { dirPath ->
      if (dirPath.isNotBlank()) ensureDir(dirPath)
    }

    files.entries.sortedBy { it.key }.forEach { (rawPath, data) ->
      val clean = rawPath.trim('/').replace('\\', '/')
      if (clean.isEmpty()) return@forEach
      val parts = clean.split('/').filter { it.isNotBlank() && it != ".." }
      if (parts.isEmpty()) return@forEach
      val parentDir =
        if (parts.size == 1) root
        else ensureDir(parts.subList(0, parts.size - 1).joinToString("/"))
      parentDir.leafFiles[parts.last()] = data
    }

    class TocSlot(
      val name: String,
      val dirNode: DirNode? = null,
      val payload: ByteArray? = null,
    ) {
      var childStartIdx: Int = 0
      var childCount: Int = 0
      var blockOffset: Int = 0
    }

    // BFS layout so every directory's direct children are contiguous in the TOC
    val toc = mutableListOf<TocSlot>()
    toc.add(TocSlot(name = root.name, dirNode = root))

    var queueCursor = 0
    while (queueCursor < toc.size) {
      val slot = toc[queueCursor]
      val dNode = slot.dirNode
      if (dNode != null) {
        val childDirs = dNode.subDirs.values.sortedBy { it.name.lowercase() }
        val childFiles = dNode.leafFiles.entries.sortedBy { it.key.lowercase() }
        val totalChildren = childDirs.size + childFiles.size
        if (totalChildren > 0) {
          slot.childStartIdx = toc.size
          slot.childCount = totalChildren
          childDirs.forEach { d -> toc.add(TocSlot(name = d.name, dirNode = d)) }
          childFiles.forEach { (fname, bytes) -> toc.add(TocSlot(name = fname, payload = bytes)) }
        }
      }
      queueCursor++
    }

    val entryCount = toc.size
    val namesOut = ByteArrayOutputStream()
    namesOut.write(0) // index 0 empty root name
    val nameOffsetCache = hashMapOf<String, Int>()
    val entryNameOffsets =
      toc.mapIndexed { idx, slot ->
        if (idx == 0) {
          0
        } else {
          nameOffsetCache.getOrPut(slot.name) {
            val off = namesOut.size()
            namesOut.write(slot.name.toByteArray(Charsets.UTF_8))
            namesOut.write(0)
            off
          }
        }
      }

    val namesBytes = namesOut.toByteArray()
    val namesPaddedLen = ((namesBytes.size + 15) / 16) * 16

    val tocSize = entryCount * 16
    val dataStart = ((16 + tocSize + namesPaddedLen + 511) / 512) * 512

    var cursor = dataStart
    for (slot in toc) {
      val payload = slot.payload
      if (payload != null) {
        slot.blockOffset = cursor / 512
        val alignedLen = ((payload.size + 511) / 512) * 512
        cursor += alignedLen.coerceAtLeast(512)
      }
    }

    val totalSize = cursor.coerceAtLeast(dataStart)
    val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

    // Header
    out.putInt(RPF7_MAGIC_LE)
    out.putInt(entryCount)
    out.putInt(namesPaddedLen)
    out.putInt(ENC_OPEN)

    // TOC entries
    for (i in toc.indices) {
      val nameOff = entryNameOffsets[i] and 0xFFFF
      val slot = toc[i]
      val payload = slot.payload
      if (payload == null) {
        out.putInt(nameOff)
        out.putInt(0x7FFFFF00)
        out.putInt(slot.childStartIdx)
        out.putInt(slot.childCount)
      } else {
        val size = payload.size
        val w0 = nameOff or ((size and 0xFFFF) shl 16)
        val w1 = ((size ushr 16) and 0xFF) or ((slot.blockOffset and 0xFFFFFF) shl 8)
        out.putInt(w0)
        out.putInt(w1)
        out.putInt(size)
        out.putInt(0)
      }
    }

    // Names table
    out.position(16 + tocSize)
    out.put(namesBytes)

    // File payloads
    for (slot in toc) {
      val payload = slot.payload
      if (payload != null && payload.isNotEmpty()) {
        out.position(slot.blockOffset * 512)
        out.put(payload)
      }
    }
    return out.array()
  }

  fun formatHexDump(
    bytes: ByteArray,
    maxBytes: Int = 512,
  ): String {
    if (bytes.isEmpty()) return "(0 byte - file kosong)"
    val limit = bytes.size.coerceAtMost(maxBytes)
    val sb = StringBuilder()
    var offset = 0
    while (offset < limit) {
      sb.append(String.format(java.util.Locale.US, "%04X: ", offset))
      val ascii = StringBuilder()
      for (col in 0 until 16) {
        val idx = offset + col
        if (idx < limit) {
          val b = bytes[idx].toInt() and 0xFF
          sb.append(String.format(java.util.Locale.US, "%02X ", b))
          ascii.append(if (b in 32..126) b.toChar() else '.')
        } else {
          sb.append("   ")
          ascii.append(' ')
        }
      }
      sb.append("| ").append(ascii).append('\n')
      offset += 16
    }
    if (bytes.size > limit) {
      sb.append("... (+${bytes.size - limit} byte lainnya)")
    }
    return sb.toString().trimEnd()
  }

  fun listZipEntries(zipBytes: ByteArray): Result<List<ArchiveEntryInfo>> {
    return try {
      val list = mutableListOf<ArchiveEntryInfo>()
      val seenDirs = hashSetOf<String>()
      ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          val clean = entry.name.trim('/').replace('\\', '/')
          if (clean.isNotEmpty()) {
            // Ensure parent directories exist in listing
            val parts = clean.split('/')
            for (d in 1 until parts.size) {
              val dirPath = parts.subList(0, d).joinToString("/")
              if (seenDirs.add(dirPath)) {
                list.add(
                  ArchiveEntryInfo(
                    fullPath = dirPath,
                    name = parts[d - 1],
                    isDirectory = true,
                    size = 0L,
                  )
                )
              }
            }
            if (entry.isDirectory) {
              if (seenDirs.add(clean)) {
                list.add(
                  ArchiveEntryInfo(
                    fullPath = clean,
                    name = clean.substringAfterLast('/'),
                    isDirectory = true,
                    size = 0L,
                  )
                )
              }
            } else {
              list.add(
                ArchiveEntryInfo(
                  fullPath = clean,
                  name = clean.substringAfterLast('/'),
                  isDirectory = false,
                  size = entry.size.coerceAtLeast(0L),
                  compressedSize = entry.compressedSize.coerceAtLeast(0L),
                )
              )
            }
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
      Result.success(list)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun extractZipEntry(
    zipBytes: ByteArray,
    targetPath: String,
  ): Result<ByteArray> {
    return try {
      val normTarget = targetPath.trim('/').replace('\\', '/')
      ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          val clean = entry.name.trim('/').replace('\\', '/')
          if (!entry.isDirectory && clean == normTarget) {
            return Result.success(zis.readBytes())
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
      Result.failure(IllegalArgumentException("Entri '$targetPath' tidak ditemukan di arsip"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
