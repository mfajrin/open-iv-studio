package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.core.OivManifest
import com.example.core.OivOperation
import com.example.core.OivPackageParser
import com.example.core.RpfAndArchiveParser
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface OivInstallTarget {
  data object ModsFolder : OivInstallTarget

  data object MainWorkspace : OivInstallTarget

  data class SafFolder(val treeUri: Uri, val folderLabel: String) : OivInstallTarget
}

data class OivInstalledRecord(
  val installId: String,
  val packageId: String,
  val name: String,
  val version: String,
  val author: String,
  val targetLabel: String,
  val installedAtMillis: Long,
  val writtenRelativePaths: List<String>,
  val backupRelativeDir: String?,
  val backedUpFiles: List<Pair<String, String>>, // (targetRelPath, backupRelPath)
)

data class OivInstallResult(
  val record: OivInstalledRecord?,
  val logs: List<String>,
  val filesWritten: Int,
  val filesDeleted: Int,
)

class OivInstallManager(
  private val context: Context,
  private val repo: WorkspaceRepository,
) {
  private val registryFile: File
    get() = File(repo.workspaceRoot, ".oiv_registry.json")

  private val backupRoot: File
    get() = File(repo.workspaceRoot, ".oiv_backups")

  suspend fun inspectOiv(source: BinarySource): Result<OivManifest> =
    withContext(Dispatchers.IO) {
      val bytes =
        repo.readBinarySource(source).getOrElse {
          return@withContext Result.failure(it)
        }
      OivPackageParser.parseOivBytes(bytes)
    }

  suspend fun installOiv(
    source: BinarySource,
    manifest: OivManifest,
    target: OivInstallTarget,
    createBackup: Boolean,
    onProgress: (logLine: String, progress: Float) -> Unit,
  ): Result<OivInstallResult> =
    withContext(Dispatchers.IO) {
      val oivBytes =
        repo.readBinarySource(source).getOrElse {
          return@withContext Result.failure(it)
        }

      val installId = UUID.randomUUID().toString().take(8)
      val logs = mutableListOf<String>()
      val writtenRelPaths = mutableListOf<String>()
      val backedUpPairs = mutableListOf<Pair<String, String>>()
      val backupDirRel = ".oiv_backups/$installId"
      val backupDir = File(repo.workspaceRoot, backupDirRel)
      var writtenCount = 0
      var deletedCount = 0

      fun emit(msg: String, p: Float) {
        logs.add(msg)
        onProgress(msg, p)
      }

      emit("Memulai instalasi: ${manifest.metadata.name} v${manifest.metadata.version}", 0.05f)

      val ops = manifest.operations
      if (ops.isEmpty()) {
        emit("Tidak ada operasi konten dalam assembly.xml.", 1f)
        return@withContext Result.success(OivInstallResult(null, logs, 0, 0))
      }

      val totalOps = ops.size.coerceAtLeast(1)

      when (target) {
        is OivInstallTarget.ModsFolder, is OivInstallTarget.MainWorkspace -> {
          val baseRel =
            if (target is OivInstallTarget.ModsFolder) "GTA V/mods" else "GTA V"

          ops.forEachIndexed { idx, op ->
            val progress = 0.1f + (0.85f * (idx + 1) / totalOps)
            when (op) {
              is OivOperation.CreateArchive -> {
                val resolvedArch = resolveWorkspaceArchiveSubpath(baseRel, op.archivePath)
                val dir = File(repo.workspaceRoot, resolvedArch)
                if (!dir.exists()) dir.mkdirs()
                emit("[DIR] Siapkan arsip: $resolvedArch", progress)
              }
              is OivOperation.AddFile -> {
                val payload =
                  RpfAndArchiveParser.extractZipEntry(oivBytes, op.sourceInZip).getOrElse { err ->
                    emit("[GAGAL] Sumber tidak ditemukan (${op.sourceInZip}): ${err.message}", progress)
                    return@forEachIndexed
                  }
                val destRel = buildWorkspaceDestRelPath(baseRel, op.archivePath, op.destinationPath)
                val destFile = File(repo.workspaceRoot, destRel)
                destFile.parentFile?.mkdirs()

                if (createBackup && destFile.exists()) {
                  backupDir.mkdirs()
                  val safeBackupName = destRel.replace('/', '_')
                  val bFile = File(backupDir, safeBackupName)
                  destFile.copyTo(bFile, overwrite = true)
                  backedUpPairs.add(destRel to "$backupDirRel/$safeBackupName")
                  emit("[BACKUP] ${destFile.name} dicadangkan", progress)
                }

                destFile.writeBytes(payload)
                writtenRelPaths.add(destRel)
                writtenCount++
                emit("[OK] + $destRel (${payload.size} B)", progress)
              }
              is OivOperation.DeleteFile -> {
                val destRel = buildWorkspaceDestRelPath(baseRel, op.archivePath, op.destinationPath)
                val destFile = File(repo.workspaceRoot, destRel)
                if (destFile.exists()) {
                  if (createBackup && destFile.isFile) {
                    backupDir.mkdirs()
                    val safeBackupName = destRel.replace('/', '_')
                    val bFile = File(backupDir, safeBackupName)
                    destFile.copyTo(bFile, overwrite = true)
                    backedUpPairs.add(destRel to "$backupDirRel/$safeBackupName")
                  }
                  if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                  deletedCount++
                  emit("[HAPUS] - $destRel", progress)
                } else {
                  emit("[LEWATI] Target hapus tidak ada: $destRel", progress)
                }
              }
            }
          }

          val record =
            OivInstalledRecord(
              installId = installId,
              packageId = manifest.metadata.id,
              name = manifest.metadata.name,
              version = manifest.metadata.version,
              author = manifest.metadata.author,
              targetLabel =
                if (target is OivInstallTarget.ModsFolder) "Folder mods/" else "Workspace GTA V",
              installedAtMillis = System.currentTimeMillis(),
              writtenRelativePaths = writtenRelPaths,
              backupRelativeDir = if (backedUpPairs.isNotEmpty()) backupDirRel else null,
              backedUpFiles = backedUpPairs,
            )
          saveInstallRecord(record)
          emit("Selesai! $writtenCount file dipasang, $deletedCount dihapus.", 1f)
          Result.success(OivInstallResult(record, logs, writtenCount, deletedCount))
        }

        is OivInstallTarget.SafFolder -> {
          val rootDocId = DocumentsContract.getTreeDocumentId(target.treeUri)
          ops.forEachIndexed { idx, op ->
            val progress = 0.1f + (0.85f * (idx + 1) / totalOps)
            when (op) {
              is OivOperation.CreateArchive -> {
                emit("[INFO] Target arsip: ${op.archivePath}", progress)
              }
              is OivOperation.AddFile -> {
                val payload =
                  RpfAndArchiveParser.extractZipEntry(oivBytes, op.sourceInZip).getOrElse { err ->
                    emit("[GAGAL] ${op.sourceInZip}: ${err.message}", progress)
                    return@forEachIndexed
                  }
                val relParts =
                  buildList {
                    if (!op.archivePath.isNullOrBlank()) {
                      addAll(op.archivePath.split('/').filter { it.isNotBlank() })
                    }
                    addAll(op.destinationPath.split('/').filter { it.isNotBlank() })
                  }
                val writeRes = writeBytesToSafTreePath(target.treeUri, rootDocId, relParts, payload)
                writeRes
                  .onSuccess {
                    writtenCount++
                    emit("[SAF OK] + ${relParts.joinToString("/")}", progress)
                  }
                  .onFailure { err ->
                    emit("[SAF GAGAL] ${relParts.joinToString("/")}: ${err.message}", progress)
                  }
              }
              is OivOperation.DeleteFile -> {
                emit("[LEWATI] Operasi hapus dilewati pada mode ekspor SAF", progress)
              }
            }
          }
          emit("Ekspor/Instalasi ke '${target.folderLabel}' selesai ($writtenCount file).", 1f)
          Result.success(OivInstallResult(null, logs, writtenCount, 0))
        }
      }
    }

  private fun resolveWorkspaceArchiveSubpath(
    baseRel: String,
    archivePath: String,
  ): String {
    val cleanArch = archivePath.trim('/').replace("..", "")
    // If installing to MainWorkspace ("GTA V") and archivePath is "update/update.rpf",
    // map to "GTA V/update" if that folder exists cleanly so sample files align seamlessly.
    if (baseRel == "GTA V" && cleanArch.equals("update/update.rpf", ignoreCase = true)) {
      return "GTA V/update"
    }
    return if (cleanArch.isEmpty()) baseRel else "$baseRel/$cleanArch"
  }

  private fun buildWorkspaceDestRelPath(
    baseRel: String,
    archivePath: String?,
    destPath: String,
  ): String {
    val cleanDest = destPath.trim('/').replace("..", "")
    val prefix =
      if (archivePath.isNullOrBlank()) baseRel
      else resolveWorkspaceArchiveSubpath(baseRel, archivePath)
    return "$prefix/$cleanDest"
  }

  private fun writeBytesToSafTreePath(
    treeUri: Uri,
    rootDocId: String,
    parts: List<String>,
    bytes: ByteArray,
  ): Result<Unit> {
    return try {
      if (parts.isEmpty()) return Result.failure(IllegalArgumentException("Path kosong"))
      var currentDocId = rootDocId
      for (i in 0 until parts.lastIndex) {
        val folderName = parts[i]
        currentDocId = findOrCreateSafChild(treeUri, currentDocId, folderName, DocumentsContract.Document.MIME_TYPE_DIR)
          ?: return Result.failure(IllegalStateException("Gagal membuat folder $folderName"))
      }
      val fileName = parts.last()
      val existingFileId = findSafChildByName(treeUri, currentDocId, fileName)
      val fileUri =
        if (existingFileId != null) {
          DocumentsContract.buildDocumentUriUsingTree(treeUri, existingFileId)
        } else {
          val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
          DocumentsContract.createDocument(context.contentResolver, parentUri, "application/octet-stream", fileName)
            ?: return Result.failure(IllegalStateException("Gagal membuat file $fileName"))
        }
      context.contentResolver.openOutputStream(fileUri, "wt")?.use { out ->
        out.write(bytes)
        out.flush()
      } ?: return Result.failure(IllegalStateException("Gagal menulis $fileName"))
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun findSafChildByName(
    treeUri: Uri,
    parentDocId: String,
    name: String,
  ): String? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      )
    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
      val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (c.moveToNext()) {
        if (c.getString(nameIdx) == name) return c.getString(idIdx)
      }
    }
    return null
  }

  private fun findOrCreateSafChild(
    treeUri: Uri,
    parentDocId: String,
    name: String,
    mimeType: String,
  ): String? {
    findSafChildByName(treeUri, parentDocId, name)?.let { return it }
    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
    val created = DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, name)
    return created?.let { DocumentsContract.getDocumentId(it) }
  }

  suspend fun getInstalledPackages(): List<OivInstalledRecord> =
    withContext(Dispatchers.IO) {
      if (!registryFile.exists()) return@withContext emptyList()
      try {
        val arr = JSONArray(registryFile.readText())
        val list = mutableListOf<OivInstalledRecord>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          val written = mutableListOf<String>()
          val wArr = obj.optJSONArray("written") ?: JSONArray()
          for (j in 0 until wArr.length()) written.add(wArr.getString(j))

          val backups = mutableListOf<Pair<String, String>>()
          val bArr = obj.optJSONArray("backups") ?: JSONArray()
          for (j in 0 until bArr.length()) {
            val bObj = bArr.getJSONObject(j)
            backups.add(bObj.getString("target") to bObj.getString("backup"))
          }
          list.add(
            OivInstalledRecord(
              installId = obj.getString("installId"),
              packageId = obj.optString("packageId", ""),
              name = obj.getString("name"),
              version = obj.optString("version", "1.0"),
              author = obj.optString("author", ""),
              targetLabel = obj.optString("targetLabel", "mods/"),
              installedAtMillis = obj.optLong("installedAt", 0L),
              writtenRelativePaths = written,
              backupRelativeDir = obj.optString("backupDir").takeIf { it.isNotBlank() },
              backedUpFiles = backups,
            )
          )
        }
        list.sortedByDescending { it.installedAtMillis }
      } catch (_: Exception) {
        emptyList()
      }
    }

  private fun saveInstallRecord(record: OivInstalledRecord) {
    val existing =
      if (registryFile.exists()) {
        runCatching { JSONArray(registryFile.readText()) }.getOrDefault(JSONArray())
      } else {
        JSONArray()
      }
    val obj =
      JSONObject().apply {
        put("installId", record.installId)
        put("packageId", record.packageId)
        put("name", record.name)
        put("version", record.version)
        put("author", record.author)
        put("targetLabel", record.targetLabel)
        put("installedAt", record.installedAtMillis)
        put("backupDir", record.backupRelativeDir ?: "")
        put("written", JSONArray(record.writtenRelativePaths))
        val bArr = JSONArray()
        record.backedUpFiles.forEach { (t, b) ->
          bArr.put(JSONObject().apply {
            put("target", t)
            put("backup", b)
          })
        }
        put("backups", bArr)
      }
    existing.put(obj)
    registryFile.parentFile?.mkdirs()
    registryFile.writeText(existing.toString(2))
  }

  suspend fun uninstallPackage(installId: String): Result<String> =
    withContext(Dispatchers.IO) {
      val records = getInstalledPackages()
      val target =
        records.firstOrNull { it.installId == installId }
          ?: return@withContext Result.failure(IllegalArgumentException("Catatan paket tidak ditemukan"))

      var restored = 0
      var removed = 0
      val backedUpTargetSet = target.backedUpFiles.map { it.first }.toSet()

      // Remove newly added files that didn't have backups
      target.writtenRelativePaths.forEach { rel ->
        if (rel !in backedUpTargetSet) {
          val f = File(repo.workspaceRoot, rel)
          if (f.exists() && f.delete()) removed++
        }
      }

      // Restore backed up files
      target.backedUpFiles.forEach { (targetRel, backupRel) ->
        val bFile = File(repo.workspaceRoot, backupRel)
        val tFile = File(repo.workspaceRoot, targetRel)
        if (bFile.exists()) {
          tFile.parentFile?.mkdirs()
          bFile.copyTo(tFile, overwrite = true)
          restored++
        }
      }

      // Cleanup backup directory
      target.backupRelativeDir?.let { bDir ->
        File(repo.workspaceRoot, bDir).deleteRecursively()
      }

      // Rewrite registry without this installId
      val remaining = records.filterNot { it.installId == installId }
      val arr = JSONArray()
      remaining.forEach { rec ->
        arr.put(
          JSONObject().apply {
            put("installId", rec.installId)
            put("packageId", rec.packageId)
            put("name", rec.name)
            put("version", rec.version)
            put("author", rec.author)
            put("targetLabel", rec.targetLabel)
            put("installedAt", rec.installedAtMillis)
            put("backupDir", rec.backupRelativeDir ?: "")
            put("written", JSONArray(rec.writtenRelativePaths))
            val bArr = JSONArray()
            rec.backedUpFiles.forEach { (t, b) ->
              bArr.put(JSONObject().apply {
                put("target", t)
                put("backup", b)
              })
            }
            put("backups", bArr)
          }
        )
      }
      registryFile.writeText(arr.toString(2))
      Result.success("${target.name} dicopot ($removed file dihapus, $restored backup dipulihkan)")
    }
}
