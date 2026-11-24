package com.example.data

import android.net.Uri

enum class ModItemKind {
  FOLDER,
  RPF_ARCHIVE,
  OIV_ZIP_ARCHIVE,
  YTD_DICTIONARY,
  XML_META,
  IMAGE_TEXTURE,
  OTHER,
}

enum class ExplorerFilter(val label: String) {
  ALL("Semua"),
  XML_META("XML / Meta"),
  TEXTURES("Tekstur / YTD"),
  ARCHIVES("Arsip"),
}

sealed interface ExplorerLocation {
  data class LocalDir(val relativePath: String) : ExplorerLocation

  data class ArchiveView(
    val archiveTitle: String,
    val archiveSource: BinarySource,
    val archiveType: ArchiveType,
    val innerPath: String,
    val parentLocalPath: String?,
  ) : ExplorerLocation

  data class SafTreeDir(
    val treeUri: Uri,
    val documentId: String,
    val displayStack: List<Pair<String, String>>, // (docId, displayName)
  ) : ExplorerLocation
}

enum class ArchiveType {
  ZIP_OR_OIV,
  RPF7,
}

sealed interface BinarySource {
  data class LocalFile(val absolutePath: String) : BinarySource

  data class SafUri(val uri: Uri, val displayName: String) : BinarySource

  data class ArchiveInner(
    val parentArchive: BinarySource,
    val archiveType: ArchiveType,
    val entryPath: String,
  ) : BinarySource
}

data class ModFileEntry(
  val id: String,
  val name: String,
  val kind: ModItemKind,
  val sizeBytes: Long,
  val badge: String,
  val subtitle: String,
  val localPath: String? = null,
  val safUri: Uri? = null,
  val safDocId: String? = null,
  val archiveInnerPath: String? = null,
  val isReadOnly: Boolean = false,
)

data class TextureItemRef(
  val name: String,
  val source: BinarySource,
)

fun classifyFileKind(
  name: String,
  isDirectory: Boolean,
): ModItemKind {
  val lower = name.lowercase()
  if (isDirectory) {
    return if (lower.endsWith(".ytd")) ModItemKind.YTD_DICTIONARY else ModItemKind.FOLDER
  }
  return when {
    lower.endsWith(".rpf") -> ModItemKind.RPF_ARCHIVE
    lower.endsWith(".oiv") || lower.endsWith(".zip") -> ModItemKind.OIV_ZIP_ARCHIVE
    lower.endsWith(".ytd") -> ModItemKind.YTD_DICTIONARY
    lower.endsWith(".xml") ||
      lower.endsWith(".meta") ||
      lower.endsWith(".ymap") ||
      lower.endsWith(".ytyp") ||
      lower.endsWith(".ini") ||
      lower.endsWith(".json") ||
      lower.endsWith(".txt") ||
      lower.endsWith(".dat") -> ModItemKind.XML_META
    lower.endsWith(".png") ||
      lower.endsWith(".jpg") ||
      lower.endsWith(".jpeg") ||
      lower.endsWith(".webp") ||
      lower.endsWith(".bmp") ||
      lower.endsWith(".dds") ||
      lower.endsWith(".tga") -> ModItemKind.IMAGE_TEXTURE
    else -> ModItemKind.OTHER
  }
}

fun formatByteSize(bytes: Long): String {
  if (bytes <= 0L) return "0 B"
  val kb = bytes / 1024.0
  if (kb < 1.0) return "$bytes B"
  val mb = kb / 1024.0
  if (mb < 1.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
  return String.format(java.util.Locale.US, "%.2f MB", mb)
}
