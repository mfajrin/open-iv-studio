package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.RpfEncryptionType
import com.example.data.ModItemKind
import com.example.data.classifyFileKind
import com.example.data.formatByteSize
import com.example.ui.RpfEditorUiState
import com.example.ui.components.FormatTagPill
import com.example.ui.components.GtaHeaderBackdrop
import com.example.ui.components.ModFileIconBadge
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily

private data class RpfVisibleItem(
  val fullPath: String,
  val name: String,
  val isDirectory: Boolean,
  val kind: ModItemKind,
  val sizeBytes: Long,
  val childCount: Int,
  val blockOffsetText: String?,
)

@Composable
fun RpfEditorScreen(
  state: RpfEditorUiState,
  editMode: Boolean,
  onOpenSampleRpf: () -> Unit,
  onOpenRpfSafUri: (Uri) -> Unit,
  onCreateNewRpf: (name: String, includeStarter: Boolean) -> Unit,
  onNavigateFolder: (String) -> Unit,
  onNavigateUp: () -> Boolean,
  onSearchChanged: (String) -> Unit,
  onSelectEntryForHex: (String?) -> Unit,
  onOpenEntryInXml: (String) -> Unit,
  onOpenEntryInImage: (String) -> Unit,
  onCreateSubfolder: (String) -> Unit,
  onCreateXmlFile: (name: String, template: String) -> Unit,
  onImportFileFromSaf: (Uri) -> Unit,
  onRenameEntry: (path: String, isDir: Boolean, newName: String) -> Unit,
  onDeleteEntry: (path: String, isDir: Boolean) -> Unit,
  onExtractEntryToMods: (String) -> Unit,
  onExtractEntryToSaf: (entryPath: String, destUri: Uri) -> Unit,
  onSaveRpf: () -> Unit,
  onExportRpfToSaf: (Uri) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCreateRpfDialog by remember { mutableStateOf(false) }
  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var showCreateXmlDialog by remember { mutableStateOf(false) }
  var showHeaderInspector by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<RpfVisibleItem?>(null) }
  var deleteTarget by remember { mutableStateOf<RpfVisibleItem?>(null) }
  var pendingExtractEntryPath by remember { mutableStateOf<String?>(null) }

  val openRpfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onOpenRpfSafUri(uri)
    }
  val importIntoRpfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onImportFileFromSaf(uri)
    }
  val exportRpfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
      if (uri != null) onExportRpfToSaf(uri)
    }
  val extractEntryLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
      val path = pendingExtractEntryPath
      pendingExtractEntryPath = null
      if (uri != null && path != null) {
        onExtractEntryToSaf(path, uri)
      }
    }

  BackHandler(enabled = state.source != null && state.currentFolderPath.isNotEmpty()) {
    onNavigateUp()
  }

  Column(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EmeraldPrimary)
      }
      return
    }

    if (state.source == null) {
      RpfEmptyStartPanel(
        onOpenSampleRpf = onOpenSampleRpf,
        onOpenRpfFromDevice = { openRpfLauncher.launch(arrayOf("*/*")) },
        onCreateNewRpfClick = { showCreateRpfDialog = true },
      )
    } else if (state.errorMessage != null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = state.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = CoralError,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openRpfLauncher.launch(arrayOf("*/*")) }) {
              Text("Pilih .rpf Lain")
            }
            OutlinedButton(onClick = onOpenSampleRpf) {
              Text("Buka sample_open.rpf")
            }
          }
        }
      }
    } else {
      val header = state.header
      val encColor =
        when (header?.encryption) {
          RpfEncryptionType.OPEN -> EmeraldPrimary
          RpfEncryptionType.AES, RpfEncryptionType.NG -> AmberSecondary
          else -> CoralError
        }

      // Header Bar with GTA V Backdrop
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        GtaHeaderBackdrop(imageAlpha = 0.4f, scrimStartAlpha = 0.9f, scrimEndAlpha = 0.65f) {
          Row(
            modifier =
              Modifier.widthIn(max = 900.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = state.archiveName + if (state.isDirty) " •" else "",
                  style = MaterialTheme.typography.titleMedium,
                  color = if (state.isDirty) AmberSecondary else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
                FormatTagPill(
                  text = header?.encryption?.name ?: "RPF7",
                  accent = encColor,
                )
              }
              val totalPayloadBytes = remember(state.files) { state.files.values.sumOf { it.size.toLong() } }
              Text(
                text =
                  if (state.isEditableOpenRpf)
                    "${state.files.size} file • ${state.directories.size} folder • ${formatByteSize(totalPayloadBytes)}"
                  else
                    "${header?.entryCount ?: 0} entri TOC • Hanya baca (${header?.encryption?.label ?: "Terenkripsi"})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Button(
                onClick = onSaveRpf,
                enabled = editMode && state.isEditableOpenRpf,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = if (state.isDirty) AmberSecondary else EmeraldPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.height(36.dp).testTag("rpf_save_button"),
              ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Simpan", style = MaterialTheme.typography.labelMedium)
              }
              IconButton(
                onClick = {
                  val outName = state.archiveName.ifBlank { "mod_archive.rpf" }
                  exportRpfLauncher.launch(outName)
                },
                modifier = Modifier.testTag("rpf_export_button"),
              ) {
                Icon(
                  Icons.Rounded.Download,
                  contentDescription = "Ekspor arsip .rpf",
                  tint = CyanTertiary,
                )
              }
            }
          }
        }
      }

      // Action Toolbar
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AssistChip(
          onClick = { importIntoRpfLauncher.launch(arrayOf("*/*")) },
          enabled = state.isEditableOpenRpf,
          label = { Text("Tambah File") },
          leadingIcon = { Icon(Icons.Rounded.UploadFile, null, Modifier.size(15.dp)) },
          modifier = Modifier.testTag("rpf_add_file_btn"),
        )
        AssistChip(
          onClick = { showCreateXmlDialog = true },
          enabled = state.isEditableOpenRpf,
          label = { Text("Buat XML/Meta") },
          leadingIcon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(15.dp)) },
          modifier = Modifier.testTag("rpf_new_xml_btn"),
        )
        AssistChip(
          onClick = { showCreateFolderDialog = true },
          enabled = state.isEditableOpenRpf,
          label = { Text("Folder Baru") },
          leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(15.dp)) },
          modifier = Modifier.testTag("rpf_new_folder_btn"),
        )
        AssistChip(
          onClick = { showHeaderInspector = true },
          label = { Text("Header RPF7") },
          leadingIcon = { Icon(Icons.Rounded.DataObject, null, Modifier.size(15.dp)) },
          modifier = Modifier.testTag("rpf_header_info_btn"),
        )
        AssistChip(
          onClick = { showCreateRpfDialog = true },
          label = { Text("Arsip Baru") },
          leadingIcon = { Icon(Icons.Rounded.AddCircleOutline, null, Modifier.size(15.dp)) },
          modifier = Modifier.testTag("rpf_create_new_archive_btn"),
        )
        AssistChip(
          onClick = { openRpfLauncher.launch(arrayOf("*/*")) },
          label = { Text("Buka Lain") },
          leadingIcon = { Icon(Icons.Rounded.FileOpen, null, Modifier.size(15.dp)) },
        )
      }

      // Main Content Area (constrained width for large Android 16 screens)
      Column(
        modifier =
          Modifier.weight(1f)
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (!state.isEditableOpenRpf) {
          Surface(
            color = AmberContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AmberSecondary.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Rounded.Info, contentDescription = null, tint = AmberSecondary, modifier = Modifier.size(18.dp))
              Text(
                text = state.diagnosticMessage
                  ?: "Arsip terenkripsi (NG/AES) atau arsip bersarang hanya mendukung inspeksi header. Gunakan RPF7 OPEN atau buat arsip .rpf baru untuk mengedit.",
                style = MaterialTheme.typography.bodySmall,
                color = AmberOnContainer,
              )
            }
          }
        }

        // Search & Breadcrumb inside RPF
        RpfSearchBar(query = state.searchQuery, onQueryChanged = onSearchChanged)
        RpfBreadcrumbBar(
          archiveName = state.archiveName,
          currentFolderPath = state.currentFolderPath,
          onNavigateUp = { onNavigateUp() },
          onNavigateFolder = onNavigateFolder,
        )

        val visibleItems =
          remember(
            state.files,
            state.directories,
            state.currentFolderPath,
            state.searchQuery,
            state.entryMetadata,
          ) {
            buildVisibleRpfItems(state)
          }

        if (visibleItems.isEmpty() && state.isEditableOpenRpf) {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.weight(1f).fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text =
                  if (state.searchQuery.isNotBlank()) "Tidak ada entri yang cocok di folder ini"
                  else "Folder dalam RPF masih kosong",
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = "Gunakan 'Tambah File', 'Buat XML/Meta', atau 'Folder Baru' di toolbar atas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("rpf_entry_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
          ) {
            items(visibleItems, key = { "${it.isDirectory}:${it.fullPath}" }) { item ->
              RpfEntryRowCard(
                item = item,
                editMode = editMode && state.isEditableOpenRpf,
                isSelected = state.selectedEntryPath == item.fullPath,
                onClick = {
                  if (item.isDirectory) {
                    onNavigateFolder(item.fullPath)
                  } else {
                    when (item.kind) {
                      ModItemKind.XML_META -> onOpenEntryInXml(item.fullPath)
                      ModItemKind.IMAGE_TEXTURE -> onOpenEntryInImage(item.fullPath)
                      else -> onSelectEntryForHex(item.fullPath)
                    }
                  }
                },
                onInspectHex = { onSelectEntryForHex(item.fullPath) },
                onExtractToMods = { onExtractEntryToMods(item.fullPath) },
                onExtractToDevice = {
                  pendingExtractEntryPath = item.fullPath
                  extractEntryLauncher.launch(item.name)
                },
                onRename = { renameTarget = item },
                onDelete = { deleteTarget = item },
              )
            }
          }
        }

        // Hex Preview Panel for Selected Entry
        if (state.selectedEntryPath != null && state.selectedHexPreview.isNotEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, CyanTertiary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("rpf_hex_preview_panel"),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Inspeksi Hex: ${state.selectedEntryPath}",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val byteSize = state.files[state.selectedEntryPath]?.size?.toLong() ?: 0L
                  Text(
                    text = "${formatByteSize(byteSize)} ($byteSize byte)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { onSelectEntryForHex(null) },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(Icons.Rounded.Close, contentDescription = "Tutup hex preview", modifier = Modifier.size(18.dp))
                }
              }
              Text(
                text = state.selectedHexPreview,
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                  ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
              )
            }
          }
        }
      }
    }
  }

  // Dialogs
  if (showCreateRpfDialog) {
    CreateNewRpfDialog(
      onDismiss = { showCreateRpfDialog = false },
      onCreate = { name, includeStarter ->
        showCreateRpfDialog = false
        onCreateNewRpf(name, includeStarter)
      },
    )
  }

  if (showCreateFolderDialog) {
    SimpleNameDialog(
      title = "Folder Baru dalam RPF",
      label = "Nama Folder (mis. common atau data)",
      initialValue = "data",
      confirmLabel = "Tambah",
      onDismiss = { showCreateFolderDialog = false },
      onConfirm = { name ->
        showCreateFolderDialog = false
        onCreateSubfolder(name)
      },
    )
  }

  if (showCreateXmlDialog) {
    CreateRpfXmlDialog(
      onDismiss = { showCreateXmlDialog = false },
      onCreate = { name, template ->
        showCreateXmlDialog = false
        onCreateXmlFile(name, template)
      },
    )
  }

  renameTarget?.let { target ->
    SimpleNameDialog(
      title = "Ubah Nama Entri RPF",
      label = "Nama Baru",
      initialValue = target.name,
      confirmLabel = "Simpan",
      onDismiss = { renameTarget = null },
      onConfirm = { newName ->
        onRenameEntry(target.fullPath, target.isDirectory, newName)
        renameTarget = null
      },
    )
  }

  deleteTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("Hapus ${target.name}?") },
      text = {
        Text(
          if (target.isDirectory) "Folder '${target.fullPath}' beserta seluruh isinya akan dihapus dari arsip RPF."
          else "File '${target.fullPath}' akan dihapus dari arsip RPF."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteEntry(target.fullPath, target.isDirectory)
            deleteTarget = null
          }
        ) {
          Text("Hapus", color = CoralError)
        }
      },
      dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } },
    )
  }

  if (showHeaderInspector && state.header != null) {
    val h = state.header
    AlertDialog(
      onDismissRequest = { showHeaderInspector = false },
      title = { Text("Inspektor Header RPF7") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Magic: ${h.magic}", style = MaterialTheme.typography.bodySmall)
          Text("Enkripsi: ${h.encryption.label}", style = MaterialTheme.typography.bodySmall)
          Text("Jumlah Entri TOC: ${h.entryCount}", style = MaterialTheme.typography.bodySmall)
          Text("Panjang Tabel Nama: ${h.namesLength} byte", style = MaterialTheme.typography.bodySmall)
          Text("Dump Header & TOC Awal:", style = MaterialTheme.typography.labelMedium, color = CyanTertiary)
          Text(
            text = state.headerHexPreview,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(max = 200.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showHeaderInspector = false }) { Text("Tutup") }
      },
    )
  }
}

private fun buildVisibleRpfItems(state: RpfEditorUiState): List<RpfVisibleItem> {
  val prefix = if (state.currentFolderPath.isEmpty()) "" else "${state.currentFolderPath}/"
  val query = state.searchQuery.trim()
  val childDirs = linkedSetOf<String>()
  val directFiles = mutableListOf<RpfVisibleItem>()

  state.directories.forEach { dir ->
    if (dir.startsWith(prefix)) {
      val rem = dir.removePrefix(prefix)
      if (rem.isNotEmpty()) {
        childDirs.add(rem.substringBefore('/'))
      }
    }
  }

  state.files.forEach { (path, bytes) ->
    if (path.startsWith(prefix)) {
      val rem = path.removePrefix(prefix)
      if (rem.contains('/')) {
        childDirs.add(rem.substringBefore('/'))
      } else if (rem.isNotEmpty()) {
        val meta = state.entryMetadata[path]
        val blockText =
          if (meta != null && meta.offset > 0L) "Blok #${meta.offset / 512}" else null
        directFiles.add(
          RpfVisibleItem(
            fullPath = path,
            name = rem,
            isDirectory = false,
            kind = classifyFileKind(rem, isDirectory = false),
            sizeBytes = bytes.size.toLong(),
            childCount = 0,
            blockOffsetText = blockText,
          )
        )
      }
    }
  }

  val folderItems =
    childDirs.sortedBy { it.lowercase() }.map { dirName ->
      val fullDir = if (prefix.isEmpty()) dirName else "$prefix$dirName"
      val dirPrefix = "$fullDir/"
      val count =
        state.files.keys.count { it.startsWith(dirPrefix) } +
          state.directories.count { it.startsWith(dirPrefix) }
      RpfVisibleItem(
        fullPath = fullDir,
        name = dirName,
        isDirectory = true,
        kind = classifyFileKind(dirName, isDirectory = true),
        sizeBytes = 0L,
        childCount = count,
        blockOffsetText = null,
      )
    }

  val combined = folderItems + directFiles.sortedBy { it.name.lowercase() }
  return if (query.isEmpty()) combined
  else combined.filter { it.name.contains(query, ignoreCase = true) }
}

@Composable
private fun RpfEmptyStartPanel(
  onOpenSampleRpf: () -> Unit,
  onOpenRpfFromDevice: () -> Unit,
  onCreateNewRpfClick: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        GtaHeaderBackdrop(imageAlpha = 0.45f, scrimStartAlpha = 0.92f, scrimEndAlpha = 0.68f) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
              Icons.Rounded.Inventory2,
              contentDescription = null,
              tint = EmeraldPrimary,
              modifier = Modifier.size(36.dp),
            )
            Text("RPF7 Archive Editor", style = MaterialTheme.typography.headlineSmall)
            Text(
              text = "Buat arsip .rpf baru, tambah/hapus/ubah nama folder & file mod, edit langsung file XML/Meta di dalam arsip, inspeksi header biner & hex dump, lalu bangun ulang dan ekspor RPF7.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = onOpenRpfFromDevice,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.weight(1f).testTag("rpf_empty_open_btn"),
              ) {
                Icon(Icons.Rounded.FileOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buka .rpf")
              }
              OutlinedButton(
                onClick = onCreateNewRpfClick,
                modifier = Modifier.weight(1f).testTag("rpf_empty_create_btn"),
              ) {
                Icon(Icons.Rounded.AddCircleOutline, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buat Baru")
              }
            }
          }
        }
      }

      Text("Sample Arsip RPF7 Bawaan", style = MaterialTheme.typography.titleMedium)
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenSampleRpf)
            .testTag("rpf_open_sample_card"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("sample_open.rpf", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "GTA V/mods/sample_open.rpf • RPF7 OPEN siap edit & rebuild",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FormatTagPill(text = "EDIT RPF", accent = EmeraldPrimary)
        }
      }
    }
  }
}

@Composable
private fun RpfSearchBar(
  query: String,
  onQueryChanged: (String) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth().height(44.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Rounded.Search,
        contentDescription = "Cari entri RPF",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(8.dp))
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
          Text(
            text = "Filter entri dalam folder RPF...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            maxLines = 1,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChanged,
          singleLine = true,
          cursorBrush = SolidColor(EmeraldPrimary),
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
          modifier = Modifier.fillMaxWidth().testTag("rpf_search_input"),
        )
      }
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChanged("") }, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Rounded.Clear, contentDescription = "Bersihkan pencarian", modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun RpfBreadcrumbBar(
  archiveName: String,
  currentFolderPath: String,
  onNavigateUp: () -> Unit,
  onNavigateFolder: (String) -> Unit,
) {
  val canUp = currentFolderPath.isNotEmpty()
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onNavigateUp,
        enabled = canUp,
        modifier = Modifier.testTag("rpf_nav_up"),
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Kembali ke folder induk",
          tint =
            if (canUp) EmeraldPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
      }
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = archiveName,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = if (!canUp) EmeraldPrimary else AmberSecondary,
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .clickable { onNavigateFolder("") }
              .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        val parts = currentFolderPath.trim('/').split('/').filter { it.isNotEmpty() }
        parts.forEachIndexed { idx, seg ->
          Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
          val path = parts.subList(0, idx + 1).joinToString("/")
          Text(
            text = seg,
            style = MaterialTheme.typography.labelMedium,
            color = if (idx == parts.lastIndex) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
              Modifier.clip(RoundedCornerShape(6.dp))
                .clickable { onNavigateFolder(path) }
                .padding(horizontal = 6.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun RpfEntryRowCard(
  item: RpfVisibleItem,
  editMode: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  onInspectHex: () -> Unit,
  onExtractToMods: () -> Unit,
  onExtractToDevice: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  val badge =
    if (item.isDirectory) "DIR"
    else item.name.substringAfterLast('.', "BIN").uppercase().take(5)
  val accent =
    when (item.kind) {
      ModItemKind.FOLDER, ModItemKind.YTD_DICTIONARY -> AmberSecondary
      ModItemKind.XML_META -> EmeraldPrimary
      ModItemKind.IMAGE_TEXTURE -> CyanTertiary
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = if (isSelected) CyanTertiary else HairlineBorder,
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("rpf_entry_${item.name}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModFileIconBadge(kind = item.kind, badge = badge)
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val sub =
          if (item.isDirectory) {
            "Folder RPF • ${item.childCount} item"
          } else {
            val offsetPart = item.blockOffsetText?.let { " • $it" } ?: ""
            "${formatByteSize(item.sizeBytes)}$offsetPart"
          }
        Text(
          text = sub,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(6.dp))
      FormatTagPill(text = badge, accent = accent)

      Box {
        IconButton(
          onClick = { menuOpen = true },
          modifier = Modifier.testTag("rpf_menu_${item.name}"),
        ) {
          Icon(
            Icons.Rounded.MoreVert,
            contentDescription = "Aksi ${item.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          if (!item.isDirectory) {
            if (item.kind == ModItemKind.XML_META || item.kind == ModItemKind.IMAGE_TEXTURE) {
              DropdownMenuItem(
                text = {
                  Text(if (item.kind == ModItemKind.XML_META) "Edit di XML Editor" else "Lihat Tekstur")
                },
                leadingIcon = { Icon(Icons.Rounded.Code, null) },
                onClick = {
                  menuOpen = false
                  onClick()
                },
              )
            }
            DropdownMenuItem(
              text = { Text("Inspeksi Hex") },
              leadingIcon = { Icon(Icons.Rounded.DataObject, null) },
              onClick = {
                menuOpen = false
                onInspectHex()
              },
            )
            DropdownMenuItem(
              text = { Text("Ekstrak ke Perangkat") },
              leadingIcon = { Icon(Icons.Rounded.Download, null) },
              onClick = {
                menuOpen = false
                onExtractToDevice()
              },
            )
            DropdownMenuItem(
              text = { Text("Ekstrak ke GTA V/mods") },
              leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
              onClick = {
                menuOpen = false
                onExtractToMods()
              },
            )
          } else {
            DropdownMenuItem(
              text = { Text("Buka Folder") },
              leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
              onClick = {
                menuOpen = false
                onClick()
              },
            )
          }
          if (editMode) {
            DropdownMenuItem(
              text = { Text("Ubah Nama") },
              leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
              onClick = {
                menuOpen = false
                onRename()
              },
            )
            DropdownMenuItem(
              text = { Text("Hapus", color = CoralError) },
              leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = CoralError) },
              onClick = {
                menuOpen = false
                onDelete()
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CreateNewRpfDialog(
  onDismiss: () -> Unit,
  onCreate: (name: String, includeStarter: Boolean) -> Unit,
) {
  var name by remember { mutableStateOf("custom_mod.rpf") }
  var includeStarter by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Buat Arsip RPF7 Baru") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nama File .rpf") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_rpf_name_input"),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { includeStarter = !includeStarter },
        ) {
          Checkbox(
            checked = includeStarter,
            onCheckedChange = { includeStarter = it },
            colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary),
          )
          Text(
            text = "Sertakan struktur awal GTA V (common/data/handling.meta, visualsettings.xml)",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = name.isNotBlank(),
        onClick = { onCreate(name, includeStarter) },
        modifier = Modifier.testTag("create_rpf_confirm_btn"),
      ) {
        Text("Buat", color = EmeraldPrimary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
  )
}

@Composable
private fun CreateRpfXmlDialog(
  onDismiss: () -> Unit,
  onCreate: (name: String, template: String) -> Unit,
) {
  var name by remember { mutableStateOf("handling.meta") }
  var template by remember { mutableStateOf("handling") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tambah File XML / Meta ke RPF") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nama File") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text("Template GTA V:", style = MaterialTheme.typography.labelMedium)
        val options =
          listOf(
            "handling" to "handling.meta",
            "vehicles" to "vehicles.meta",
            "assembly" to "assembly.xml",
            "blank" to "XML Kosong",
          )
        options.forEach { (key, label) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { template = key },
          ) {
            RadioButton(selected = template == key, onClick = { template = key })
            Text(label, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, template) }) {
        Text("Tambahkan", color = EmeraldPrimary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
  )
}

@Composable
private fun SimpleNameDialog(
  title: String,
  label: String,
  initialValue: String,
  confirmLabel: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var value by remember { mutableStateOf(initialValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) {
        Text(confirmLabel, color = EmeraldPrimary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
  )
}
