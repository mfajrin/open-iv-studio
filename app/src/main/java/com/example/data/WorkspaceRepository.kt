package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.R
import com.example.core.ArchiveEntryInfo
import com.example.core.RpfAndArchiveParser
import com.example.core.RpfHeaderInfo
import com.example.core.TextureDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArchiveListingResult(
  val entries: List<ModFileEntry>,
  val rpfHeader: RpfHeaderInfo? = null,
  val diagnostic: String? = null,
)

class WorkspaceRepository(private val context: Context) {
  val workspaceRoot: File
    get() = File(context.filesDir, "openv_workspace")

  suspend fun ensureSampleWorkspaceInitialized() =
    withContext(Dispatchers.IO) {
      val marker = File(workspaceRoot, ".initialized_v1")
      if (!marker.exists()) {
        seedSampleWorkspace()
        marker.writeText("1")
      }
    }

  suspend fun resetSampleWorkspace() =
    withContext(Dispatchers.IO) {
      if (workspaceRoot.exists()) {
        workspaceRoot.deleteRecursively()
      }
      seedSampleWorkspace()
      File(workspaceRoot, ".initialized_v1").writeText("1")
    }

  private fun seedSampleWorkspace() {
    val gtaRoot = File(workspaceRoot, "GTA V")
    val dataDir = File(gtaRoot, "update/common/data")
    val levelsDir = File(dataDir, "levels/gta5")
    val vehTexDir = File(gtaRoot, "update/x64/textures/vehshare_livery.ytd")
    val hudTexDir = File(gtaRoot, "update/x64/textures/hud_minimap.ytd")
    val modsDir = File(gtaRoot, "mods")

    dataDir.mkdirs()
    levelsDir.mkdirs()
    vehTexDir.mkdirs()
    hudTexDir.mkdirs()
    modsDir.mkdirs()

    File(dataDir, "handling.meta").writeText(SAMPLE_HANDLING_META)
    File(levelsDir, "vehicles.meta").writeText(SAMPLE_VEHICLES_META)
    File(dataDir, "carvariations.meta").writeText(SAMPLE_CARVARIATIONS_META)
    File(dataDir, "visualsettings.xml").writeText(SAMPLE_VISUAL_XML)

    // Convert bundled generated artwork into real PNG & DDS files inside sample .ytd folders
    val vehicleBmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_tex_vehicle_paint)
    if (vehicleBmp != null) {
      val pngBytes = TextureDecoder.bitmapToPngBytes(vehicleBmp)
      val ddsBytes = TextureDecoder.encodeToDdsUncompressed(vehicleBmp)
      File(vehTexDir, "adder_carbon_livery.png").writeBytes(pngBytes)
      File(vehTexDir, "adder_carbon_livery.dds").writeBytes(ddsBytes)
    }

    val hudBmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_tex_hud_minimap)
    if (hudBmp != null) {
      val pngBytes = TextureDecoder.bitmapToPngBytes(hudBmp)
      val ddsBytes = TextureDecoder.encodeToDdsUncompressed(hudBmp)
      File(hudTexDir, "radar_downtown_ls.png").writeBytes(pngBytes)
      File(hudTexDir, "radar_downtown_ls.dds").writeBytes(ddsBytes)
    }

    // Build a genuine .oiv (OpenIV Package ZIP) in mods/
    val oivFile = File(modsDir, "Custom_Livery_Pack.oiv")
    val oivBaos = ByteArrayOutputStream()
    ZipOutputStream(oivBaos).use { zos ->
      zos.putNextEntry(ZipEntry("assembly.xml"))
      zos.write(SAMPLE_OIV_ASSEMBLY_XML.toByteArray(Charsets.UTF_8))
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("content/data/handling_patch.meta"))
      zos.write(SAMPLE_HANDLING_META.toByteArray(Charsets.UTF_8))
      zos.closeEntry()

      if (vehicleBmp != null) {
        zos.putNextEntry(ZipEntry("content/textures/adder_carbon_livery.png"))
        zos.write(TextureDecoder.bitmapToPngBytes(vehicleBmp))
        zos.closeEntry()
      }
    }
    oivFile.writeBytes(oivBaos.toByteArray())

    // Build a genuine unencrypted RPF7 OPEN archive in mods/
    val rpfBytes =
      RpfAndArchiveParser.buildOpenRpf7(
        mapOf(
          "patch_config.xml" to SAMPLE_VISUAL_XML.toByteArray(Charsets.UTF_8),
          "handling_override.meta" to SAMPLE_HANDLING_META.toByteArray(Charsets.UTF_8),
          "readme_mod.txt" to
            "OpenV Studio RPF7 Sample Archive\nFormat: RPF7 OPEN (Unencrypted)\nBerisi contoh file konfigurasi mod GTA V.".toByteArray(
              Charsets.UTF_8
            ),
        )
      )
    File(modsDir, "sample_open.rpf").writeBytes(rpfBytes)
  }

  suspend fun listLocalDirectory(relativePath: String): List<ModFileEntry> =
    withContext(Dispatchers.IO) {
      val clean = relativePath.trim('/').replace("..", "")
      val targetDir = if (clean.isEmpty()) workspaceRoot else File(workspaceRoot, clean)
      if (!targetDir.exists() || !targetDir.isDirectory) return@withContext emptyList()

      val files = targetDir.listFiles()?.toList().orEmpty()
      files
        .filter { !it.name.startsWith(".") }
        .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        .map { file ->
          val kind = classifyFileKind(file.name, file.isDirectory)
          val rel =
            if (clean.isEmpty()) file.name else "$clean/${file.name}"
          val subCount =
            if (file.isDirectory) file.listFiles()?.count { !it.name.startsWith(".") } ?: 0 else 0
          ModFileEntry(
            id = "local:$rel",
            name = file.name,
            kind = kind,
            sizeBytes = if (file.isDirectory) 0L else file.length(),
            badge = badgeForKind(file.name, kind),
            subtitle =
              when (kind) {
                ModItemKind.YTD_DICTIONARY -> "Texture Dictionary • $subCount tekstur"
                ModItemKind.FOLDER -> "Folder • $subCount item"
                ModItemKind.RPF_ARCHIVE -> "Arsip RAGE • ${formatByteSize(file.length())}"
                ModItemKind.OIV_ZIP_ARCHIVE -> "Paket Mod • ${formatByteSize(file.length())}"
                else -> "${file.extension.uppercase().ifEmpty { "FILE" }} • ${formatByteSize(file.length())}"
              },
            localPath = rel,
          )
        }
    }

  suspend fun listSafDirectory(
    treeUri: Uri,
    documentId: String,
  ): List<ModFileEntry> =
    withContext(Dispatchers.IO) {
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
      val projection =
        arrayOf(
          DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,
          DocumentsContract.Document.COLUMN_MIME_TYPE,
          DocumentsContract.Document.COLUMN_SIZE,
        )
      val results = mutableListOf<ModFileEntry>()
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
        while (cursor.moveToNext()) {
          val docId = cursor.getString(idCol)
          val name = cursor.getString(nameCol) ?: "unknown"
          val mime = cursor.getString(mimeCol) ?: ""
          val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
          val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
          val kind = classifyFileKind(name, isDir)
          val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
          results.add(
            ModFileEntry(
              id = "saf:$docId",
              name = name,
              kind = kind,
              sizeBytes = size,
              badge = badgeForKind(name, kind),
              subtitle =
                if (isDir) "Folder Perangkat"
                else "${name.substringAfterLast('.', "FILE").uppercase()} • ${formatByteSize(size)}",
              safUri = docUri,
              safDocId = docId,
            )
          )
        }
      }
      results.sortedWith(
        compareBy<ModFileEntry>(
          { it.kind != ModItemKind.FOLDER && it.kind != ModItemKind.YTD_DICTIONARY },
          { it.name.lowercase() },
        )
      )
    }

  suspend fun listArchiveDirectory(
    archiveSource: BinarySource,
    archiveType: ArchiveType,
    innerPath: String,
  ): Result<ArchiveListingResult> =
    withContext(Dispatchers.IO) {
      val bytesResult = readBinarySource(archiveSource)
      val bytes = bytesResult.getOrElse { return@withContext Result.failure(it) }
      val cleanInner = innerPath.trim('/').replace('\\', '/')

      when (archiveType) {
        ArchiveType.ZIP_OR_OIV -> {
          val all = RpfAndArchiveParser.listZipEntries(bytes).getOrElse { return@withContext Result.failure(it) }
          val children = filterArchiveChildren(all, cleanInner)
          Result.success(
            ArchiveListingResult(
              entries = children,
              diagnostic = "Arsip ${if (archiveSource.displayLabel().endsWith(".oiv", true)) "OpenIV (.oiv)" else "ZIP"} • ${all.size} total entri",
            )
          )
        }
        ArchiveType.RPF7 -> {
          val parsed = RpfAndArchiveParser.parseRpf7(bytes).getOrElse { return@withContext Result.failure(it) }
          val children = filterArchiveChildren(parsed.entries, cleanInner)
          Result.success(
            ArchiveListingResult(
              entries = children,
              rpfHeader = parsed.header,
              diagnostic = parsed.diagnosticMessage,
            )
          )
        }
      }
    }

  private fun filterArchiveChildren(
    all: List<ArchiveEntryInfo>,
    cleanInner: String,
  ): List<ModFileEntry> {
    val prefix = if (cleanInner.isEmpty()) "" else "$cleanInner/"
    val direct =
      all.filter { entry ->
        if (!entry.fullPath.startsWith(prefix)) return@filter false
        val remainder = entry.fullPath.removePrefix(prefix)
        remainder.isNotEmpty() && !remainder.contains('/')
      }
    return direct
      .sortedWith(compareBy<ArchiveEntryInfo>({ !it.isDirectory }, { it.name.lowercase() }))
      .map { entry ->
        val kind = classifyFileKind(entry.name, entry.isDirectory)
        ModFileEntry(
          id = "arch:${entry.fullPath}",
          name = entry.name,
          kind = kind,
          sizeBytes = entry.size,
          badge = badgeForKind(entry.name, kind),
          subtitle =
            if (entry.isDirectory) "Folder Arsip"
            else "${entry.name.substringAfterLast('.', "BIN").uppercase()} • ${formatByteSize(entry.size)}",
          archiveInnerPath = entry.fullPath,
          isReadOnly = true,
        )
      }
  }

  suspend fun readBinarySource(source: BinarySource): Result<ByteArray> =
    withContext(Dispatchers.IO) {
      try {
        when (source) {
          is BinarySource.LocalFile -> {
            val f = File(source.absolutePath)
            if (!f.exists()) Result.failure(IllegalStateException("File tidak ditemukan: ${f.name}"))
            else Result.success(f.readBytes())
          }
          is BinarySource.SafUri -> {
            val bytes =
              context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Tidak dapat membuka URI"))
            Result.success(bytes)
          }
          is BinarySource.ArchiveInner -> {
            val parentBytes =
              readBinarySource(source.parentArchive).getOrElse {
                return@withContext Result.failure(it)
              }
            when (source.archiveType) {
              ArchiveType.ZIP_OR_OIV ->
                RpfAndArchiveParser.extractZipEntry(parentBytes, source.entryPath)
              ArchiveType.RPF7 -> {
                val parsed =
                  RpfAndArchiveParser.parseRpf7(parentBytes).getOrElse {
                    return@withContext Result.failure(it)
                  }
                val entry =
                  parsed.entries.firstOrNull { it.fullPath == source.entryPath }
                    ?: return@withContext Result.failure(IllegalArgumentException("Entri RPF7 tidak ditemukan"))
                RpfAndArchiveParser.extractRpf7Entry(parentBytes, entry)
              }
            }
          }
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun readTextSource(source: BinarySource): Result<String> =
    withContext(Dispatchers.IO) {
      readBinarySource(source).map { bytes -> String(bytes, Charsets.UTF_8) }
    }

  suspend fun writeTextSource(
    source: BinarySource,
    content: String,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        when (source) {
          is BinarySource.LocalFile -> {
            File(source.absolutePath).writeText(content, Charsets.UTF_8)
            Result.success(Unit)
          }
          is BinarySource.SafUri -> {
            context.contentResolver.openOutputStream(source.uri, "wt")?.use { out ->
              out.write(content.toByteArray(Charsets.UTF_8))
              out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("File perangkat bersifat baca-saja"))
            Result.success(Unit)
          }
          is BinarySource.ArchiveInner -> {
            Result.failure(UnsupportedOperationException("File di dalam arsip tidak dapat ditulis langsung; ekspor ke workspace atau file lokal."))
          }
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun saveTextToLocalWorkspace(
    relativeFolder: String,
    fileName: String,
    content: String,
  ): Result<BinarySource.LocalFile> =
    withContext(Dispatchers.IO) {
      try {
        val dir =
          if (relativeFolder.isBlank()) File(workspaceRoot, "GTA V/mods")
          else File(workspaceRoot, relativeFolder.trim('/'))
        dir.mkdirs()
        val safeName = fileName.replace("/", "_").ifBlank { "untitled.xml" }
        val target = File(dir, safeName)
        target.writeText(content, Charsets.UTF_8)
        Result.success(BinarySource.LocalFile(target.absolutePath))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun createFolderInLocal(
    relativeParent: String,
    folderName: String,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val parent =
          if (relativeParent.isBlank()) workspaceRoot else File(workspaceRoot, relativeParent)
        val safe = folderName.replace("/", "_").trim()
        if (safe.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama folder kosong"))
        val newDir = File(parent, safe)
        if (newDir.exists()) return@withContext Result.failure(IllegalStateException("Folder sudah ada"))
        if (!newDir.mkdirs()) return@withContext Result.failure(IllegalStateException("Gagal membuat folder"))
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun deleteLocalItem(relativePath: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val target = File(workspaceRoot, relativePath)
        if (!target.exists()) return@withContext Result.failure(IllegalStateException("Item tidak ditemukan"))
        val ok = if (target.isDirectory) target.deleteRecursively() else target.delete()
        if (ok) Result.success(Unit) else Result.failure(IllegalStateException("Gagal menghapus"))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun renameLocalItem(
    relativePath: String,
    newName: String,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val target = File(workspaceRoot, relativePath)
        val cleanNew = newName.replace("/", "_").trim()
        if (cleanNew.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama tidak valid"))
        val dest = File(target.parentFile, cleanNew)
        if (dest.exists()) return@withContext Result.failure(IllegalStateException("Nama sudah digunakan"))
        if (target.renameTo(dest)) Result.success(Unit)
        else Result.failure(IllegalStateException("Gagal mengubah nama"))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun exportBytesToSafUri(
    uri: Uri,
    bytes: ByteArray,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
          out.write(bytes)
          out.flush()
        } ?: return@withContext Result.failure(IllegalStateException("Gagal menulis ke lokasi tujuan"))
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  fun collectSiblingTexturesLocal(filePath: String): List<TextureItemRef> {
    val f = File(filePath)
    val parent = f.parentFile ?: return listOf(TextureItemRef(f.name, BinarySource.LocalFile(f.absolutePath)))
    val imgs =
      parent
        .listFiles()
        ?.filter { !it.isDirectory && classifyFileKind(it.name, false) == ModItemKind.IMAGE_TEXTURE }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
    return if (imgs.isEmpty()) {
      listOf(TextureItemRef(f.name, BinarySource.LocalFile(f.absolutePath)))
    } else {
      imgs.map { TextureItemRef(it.name, BinarySource.LocalFile(it.absolutePath)) }
    }
  }

  fun resolveSafDisplayName(uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return c.getString(idx) ?: "file"
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
  }

  fun getLocalFile(relativePath: String): File = File(workspaceRoot, relativePath)

  private fun BinarySource.displayLabel(): String =
    when (this) {
      is BinarySource.LocalFile -> File(absolutePath).name
      is BinarySource.SafUri -> displayName
      is BinarySource.ArchiveInner -> entryPath.substringAfterLast('/')
    }

  private fun badgeForKind(
    name: String,
    kind: ModItemKind,
  ): String {
    val ext = name.substringAfterLast('.', "").uppercase()
    return when (kind) {
      ModItemKind.FOLDER -> "DIR"
      ModItemKind.YTD_DICTIONARY -> "YTD"
      ModItemKind.RPF_ARCHIVE -> "RPF7"
      ModItemKind.OIV_ZIP_ARCHIVE -> if (ext == "OIV") "OIV" else "ZIP"
      ModItemKind.XML_META -> ext.ifEmpty { "XML" }
      ModItemKind.IMAGE_TEXTURE -> ext.ifEmpty { "IMG" }
      ModItemKind.OTHER -> ext.ifEmpty { "BIN" }
    }
  }

  companion object {
    val SAMPLE_HANDLING_META =
      """<?xml version="1.0" encoding="UTF-8"?>
<CHandlingDataMgr>
  <HandlingData>
    <Item type="CHandlingData">
      <handlingName>ADDER</handlingName>
      <fMass value="1650.000000" />
      <fInitialDragCoeff value="8.500000" />
      <fPercentSubmerged value="85.000000" />
      <vecCentreOfMassOffset x="0.000000" y="0.050000" z="-0.080000" />
      <vecInertiaMultiplier x="1.000000" y="1.050000" z="1.150000" />
      <fDriveBiasFront value="0.300000" />
      <nInitialDriveGears value="7" />
      <fInitialDriveForce value="0.410000" />
      <fDriveInertia value="1.000000" />
      <fClutchChangeRateScaleUpShift value="2.800000" />
      <fClutchChangeRateScaleDownShift value="2.600000" />
      <fInitialDriveMaxFlatVel value="178.000000" />
      <fBrakeForce value="1.150000" />
      <fTractionCurveMax value="2.750000" />
      <fTractionCurveMin value="2.450000" />
      <fSuspensionForce value="2.600000" />
      <fCollisionDamageMult value="0.750000" />
      <fMonetaryValue value="1000000" />
      <strHandlingFlags>440100</strHandlingFlags>
    </Item>
    <Item type="CHandlingData">
      <handlingName>ZENTORNO</handlingName>
      <fMass value="1450.000000" />
      <fInitialDragCoeff value="8.000000" />
      <fDriveBiasFront value="0.250000" />
      <nInitialDriveGears value="6" />
      <fInitialDriveForce value="0.435000" />
      <fInitialDriveMaxFlatVel value="175.000000" />
      <fBrakeForce value="1.200000" />
      <fTractionCurveMax value="2.850000" />
    </Item>
  </HandlingData>
</CHandlingDataMgr>"""

    val SAMPLE_VEHICLES_META =
      """<?xml version="1.0" encoding="UTF-8"?>
<CVehicleModelInfo__InitDataList>
  <residentTxd>vehshare</residentTxd>
  <residentAnims />
  <InitDatas>
    <Item>
      <modelName>adder</modelName>
      <txdName>adder</txdName>
      <handlingId>ADDER</handlingId>
      <gameName>ADDER</gameName>
      <vehicleMakeName>TRUFFADE</vehicleMakeName>
      <expressionDictName>null</expressionDictName>
      <layout>LAYOUT_LOW</layout>
      <vehicleClass>VC_SUPER</vehicleClass>
      <flags>FLAG_SPORTS FLAG_NO_RESPRAY</flags>
      <swankness>SWANKNESS_5</swankness>
      <maxPassengers value="2" />
    </Item>
  </InitDatas>
</CVehicleModelInfo__InitDataList>"""

    val SAMPLE_CARVARIATIONS_META =
      """<?xml version="1.0" encoding="UTF-8"?>
<CVehicleModelInfoVariation>
  <variationData>
    <Item>
      <modelName>adder</modelName>
      <colors>
        <Item>
          <indices content="char_array">
            135
            12
            135
            156
          </indices>
          <liveries>
            <Item value="true" />
            <Item value="false" />
          </liveries>
        </Item>
      </colors>
      <kits>
        <Item>164_adder_modkit</Item>
      </kits>
      <windowsWithExposedEdges />
      <plateProbabilities>
        <Probabilities />
      </plateProbabilities>
      <lightSettings value="1" />
      <sirenSettings value="0" />
    </Item>
  </variationData>
</CVehicleModelInfoVariation>"""

    val SAMPLE_VISUAL_XML =
      """<?xml version="1.0" encoding="UTF-8"?>
<!-- OpenV Studio - Konfigurasi Visual & Shader -->
<VisualSettings>
  <TimeCycle>
    <fogDensity value="0.0042" />
    <skyBloomIntensity value="1.3500" />
  </TimeCycle>
  <VehicleLights>
    <headlightIntensity value="18.50" />
    <emissiveMultiplier value="2.40" />
  </VehicleLights>
</VisualSettings>"""

    val SAMPLE_OIV_ASSEMBLY_XML =
      """<?xml version="1.0" encoding="UTF-8"?>
<package version="2.2" id="{A4F2C810-9B31-4E91-9A2D-112233445566}" target="Five">
  <metadata>
    <name>Truffade Carbon Livery Pack</name>
    <version>
      <major>1</major>
      <minor>2</minor>
    </version>
    <author>
      <displayName>Los Santos Modding Lab</displayName>
    </author>
    <description>Paket tekstur karbon emerald dan tuning handling.meta untuk Adder.</description>
  </metadata>
  <content>
    <archive path="update/update.rpf" createIfNotExist="True" type="RPF7">
      <add source="content/data/handling_patch.meta">common/data/handling.meta</add>
      <add source="content/textures/adder_carbon_livery.png">x64/textures/adder_livery.png</add>
    </archive>
  </content>
</package>"""
  }
}
