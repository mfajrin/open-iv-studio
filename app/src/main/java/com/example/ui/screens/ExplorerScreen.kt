package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ArchiveType
import com.example.data.BinarySource
import com.example.data.ExplorerFilter
import com.example.data.ExplorerLocation
import com.example.data.ModFileEntry
import com.example.data.ModItemKind
import com.example.ui.ExplorerUiState
import com.example.ui.components.FormatTagPill
import com.example.ui.components.ModFileIconBadge
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.ElevatedContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder

@Composable
fun ExplorerScreen(
  state: ExplorerUiState,
  editMode: Boolean,
  onNavigateUp: () -> Boolean,
  onOpenWorkspaceHome: () -> Unit,
  onOpenLocation: (ExplorerLocation) -> Unit,
  onEntryClicked: (ModFileEntry) -> Unit,
  onFilterSelected: (ExplorerFilter) -> Unit,
  onSearchChanged: (String) -> Unit,
  onSafTreePicked: (Uri) -> Unit,
  onSafFilePicked: (Uri) -> Unit,
  onInstallOivPicked: (Uri) -> Unit,
  onQuickOpenXml: (String) -> Unit,
  onQuickOpenImage: (String) -> Unit,
  onQuickOpenOiv: (String, String) -> Unit,
  onQuickOpenRpfEditor: () -> Unit,
  onOpenRpfInEditor: (String, BinarySource) -> Unit,
  onCreateNewXml: (String, String) -> Unit,
  onCreateNewFolder: (String) -> Unit,
  onRenameEntry: (ModFileEntry, String) -> Unit,
  onDeleteEntry: (ModFileEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<ModFileEntry?>(null) }
  var deleteTarget by remember { mutableStateOf<ModFileEntry?>(null) }

  val canNavigateUp =
    when (val loc = state.location) {
      is ExplorerLocation.LocalDir -> loc.relativePath.trim('/') !in listOf("", "GTA V")
      is ExplorerLocation.ArchiveView -> true
      is ExplorerLocation.SafTreeDir -> true
    }

  val isRootWorkspace =
    (state.location as? ExplorerLocation.LocalDir)?.relativePath?.trim('/') == "GTA V"

  BackHandler(enabled = canNavigateUp) {
    onNavigateUp()
  }

  val openDocumentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onSafFilePicked(uri)
    }
  val openOivLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onInstallOivPicked(uri)
    }
  val openTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) onSafTreePicked(uri)
    }

  val isLocalDir = state.location is ExplorerLocation.LocalDir

  val filteredEntries =
    remember(state.entries, state.filter, state.searchQuery) {
      state.entries.filter { entry ->
        val matchesSearch =
          state.searchQuery.isBlank() ||
            entry.name.contains(state.searchQuery, ignoreCase = true)
        val matchesFilter =
          when (state.filter) {
            ExplorerFilter.ALL -> true
            ExplorerFilter.XML_META ->
              entry.kind == ModItemKind.XML_META || entry.kind == ModItemKind.FOLDER
            ExplorerFilter.TEXTURES ->
              entry.kind == ModItemKind.IMAGE_TEXTURE ||
                entry.kind == ModItemKind.YTD_DICTIONARY ||
                entry.kind == ModItemKind.FOLDER
            ExplorerFilter.ARCHIVES ->
              entry.kind == ModItemKind.RPF_ARCHIVE ||
                entry.kind == ModItemKind.OIV_ZIP_ARCHIVE ||
                entry.kind == ModItemKind.FOLDER
          }
        matchesSearch && matchesFilter
      }
    }

  Box(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_gtav_header_bg),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alpha = if (isRootWorkspace) 0.42f else 0.24f,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              0.0f to MaterialTheme.colorScheme.background.copy(alpha = if (isRootWorkspace) 0.52f else 0.72f),
              0.45f to MaterialTheme.colorScheme.background.copy(alpha = if (isRootWorkspace) 0.74f else 0.86f),
              1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
            )
          )
    )

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      floatingActionButton = {
        if (editMode && isLocalDir && !isRootWorkspace) {
          FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = EmeraldPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("explorer_create_fab"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Buat File atau Folder Baru")
          }
        }
      },
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
      // Compact Top Control Strip: Search Pill + Breadcrumb
      Column(
        modifier =
          Modifier.widthIn(max = 900.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CompactSearchBar(
          query = state.searchQuery,
          onQueryChanged = onSearchChanged,
        )
        BreadcrumbRow(
          location = state.location,
          canNavigateUp = canNavigateUp,
          onNavigateUp = { onNavigateUp() },
          onOpenLocation = onOpenLocation,
        )
      }

      // Main Scrollable Feed
      when {
        state.isLoading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EmeraldPrimary)
          }
        }
        state.errorMessage != null -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = state.errorMessage,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        else -> {
          LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("explorer_file_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            val isHomeDashboard =
              isRootWorkspace && state.searchQuery.isBlank() && state.filter == ExplorerFilter.ALL

            // Root Home Dashboard Showcase (clean, modern, uncluttered)
            if (isHomeDashboard) {
              item(key = "hero_banner") {
                ModernHeroCard()
              }

              item(key = "bento_actions") {
                QuickActionsBentoGrid(
                  onOpenFile = { openDocumentLauncher.launch(arrayOf("*/*")) },
                  onOpenFolder = { openTreeLauncher.launch(null) },
                  onInstallOiv = { openOivLauncher.launch(arrayOf("*/*")) },
                  onWorkspaceHome = { onOpenLocation(ExplorerLocation.LocalDir("GTA V/mods")) },
                )
              }

              item(key = "quick_shortcuts") {
                QuickShortcutsSection(
                  onOpenHandlingXml = {
                    onQuickOpenXml("GTA V/update/common/data/handling.meta")
                  },
                  onOpenTextureDds = {
                    onQuickOpenImage("GTA V/update/x64/textures/vehshare_livery.ytd/adder_carbon_livery.dds")
                  },
                  onOpenSampleOiv = {
                    onQuickOpenOiv("Custom_Livery_Pack.oiv", "GTA V/mods/Custom_Livery_Pack.oiv")
                  },
                  onOpenSampleRpf = onQuickOpenRpfEditor,
                )
              }
            }

            if (!isHomeDashboard) {
              // Filter Pills
              item(key = "filter_strip") {
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ExplorerFilter.entries.forEach { filter ->
                  FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldOnContainer,
                      ),
                    modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
                  )
                }
              }
            }

            // Diagnostic banner for RPF7 / OIV / YTD
            state.diagnosticBanner?.let { info ->
              item(key = "diagnostic_banner") {
                val loc = state.location
                val isRpfView = loc is ExplorerLocation.ArchiveView && loc.archiveType == ArchiveType.RPF7
                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  color = CyanContainer.copy(alpha = 0.45f),
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, CyanTertiary.copy(alpha = 0.35f)),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      Icons.Rounded.Info,
                      contentDescription = null,
                      tint = CyanTertiary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                      text = info,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f),
                    )
                    if (isRpfView) {
                      val rpfLoc = loc as ExplorerLocation.ArchiveView
                      Spacer(Modifier.width(8.dp))
                      TextButton(
                        onClick = { onOpenRpfInEditor(rpfLoc.archiveTitle, rpfLoc.archiveSource) },
                        modifier = Modifier.testTag("explorer_edit_rpf_btn"),
                      ) {
                        Text(
                          text = "Edit RPF",
                          color = EmeraldPrimary,
                          fontWeight = FontWeight.SemiBold,
                        )
                      }
                    }
                  }
                }
              }
            }

            // Directory Header
            item(key = "dir_section_header") {
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = if (isRootWorkspace) "Direktori GTA V" else "Isi Direktori",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                FormatTagPill(
                  text = "${filteredEntries.size} ITEM",
                  accent = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (filteredEntries.isEmpty()) {
              item(key = "empty_state") {
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(16.dp),
                  border = BorderStroke(1.dp, HairlineBorder),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    Icon(
                      Icons.Rounded.FolderOpen,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                      text =
                        if (state.searchQuery.isNotBlank()) "Tidak ada file yang cocok"
                        else "Folder atau arsip ini kosong",
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                      text =
                        if (editMode && isLocalDir) "Tekan tombol + untuk membuat file XML/Meta atau folder baru."
                        else "Gunakan Buka File atau Buka Folder untuk memuat aset dari perangkat.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            } else {
              items(filteredEntries, key = { it.id }) { entry ->
                ExplorerEntryCard(
                  entry = entry,
                  editMode = editMode && isLocalDir,
                  onClick = { onEntryClicked(entry) },
                  onRename = { renameTarget = entry },
                  onDelete = { deleteTarget = entry },
                )
              }
            }
            }
          }
        }
      }
    }
  }
  }

  if (showCreateDialog) {
    CreateItemDialog(
      onDismiss = { showCreateDialog = false },
      onCreateFile = { name, tpl ->
        showCreateDialog = false
        onCreateNewXml(name, tpl)
      },
      onCreateFolder = { name ->
        showCreateDialog = false
        onCreateNewFolder(name)
      },
    )
  }

  renameTarget?.let { target ->
    RenameEntryDialog(
      currentName = target.name,
      onDismiss = { renameTarget = null },
      onConfirm = { newName ->
        onRenameEntry(target, newName)
        renameTarget = null
      },
    )
  }

  deleteTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("Hapus ${target.name}?") },
      text = { Text("Item ini akan dihapus permanen dari workspace lokal.") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteEntry(target)
            deleteTarget = null
          }
        ) {
          Text("Hapus", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
      },
    )
  }
}

@Composable
private fun CompactSearchBar(
  query: String,
  onQueryChanged: (String) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.85f)),
    modifier = Modifier.fillMaxWidth().height(48.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Rounded.Search,
        contentDescription = "Cari",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(10.dp))
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
          Text(
            text = "Cari .meta, .xml, .ytd, .oiv, .rpf, .dds...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChanged,
          singleLine = true,
          cursorBrush = SolidColor(EmeraldPrimary),
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
          modifier = Modifier.fillMaxWidth().testTag("explorer_search_input"),
        )
      }
      if (query.isNotEmpty()) {
        IconButton(
          onClick = { onQueryChanged("") },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            Icons.Rounded.Clear,
            contentDescription = "Hapus pencarian",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ModernHeroCard() {
  Surface(
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.34f)),
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_mod_banner),
        contentDescription = "Latar wireframe kota Los Santos GTA V",
        contentScale = ContentScale.Crop,
        alpha = 0.28f,
        modifier = Modifier.matchParentSize(),
      )
      Box(
        modifier =
          Modifier.matchParentSize()
            .background(
              Brush.linearGradient(
                listOf(
                  MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                  MaterialTheme.colorScheme.background.copy(alpha = 0.42f),
                )
              )
            )
      )
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FormatTagPill(text = "GTA V SUITE", accent = EmeraldPrimary)
          Text(
            text = "RPF7 • OIV • XML • DDS",
            style = MaterialTheme.typography.labelSmall,
            color = CyanTertiary,
          )
        }
        Text(
          text = "Workstation Modding & Arsip",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "Kelola file konfigurasi .meta, instal paket mod .oiv, dan inspeksi tekstur .dds / .ytd langsung dari perangkat Anda.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun QuickActionsBentoGrid(
  onOpenFile: () -> Unit,
  onOpenFolder: () -> Unit,
  onInstallOiv: () -> Unit,
  onWorkspaceHome: () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val wideRow = maxWidth >= 560.dp
    if (wideRow) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BentoActionTile(
          title = "Buka File",
          subtitle = "XML, DDS, RPF",
          icon = Icons.Rounded.FileOpen,
          accent = EmeraldPrimary,
          container = EmeraldContainer,
          onClick = onOpenFile,
          modifier = Modifier.weight(1f).testTag("btn_open_saf_file"),
        )
        BentoActionTile(
          title = "Buka Folder",
          subtitle = "Direktori SAF",
          icon = Icons.Rounded.FolderOpen,
          accent = CyanTertiary,
          container = CyanContainer,
          onClick = onOpenFolder,
          modifier = Modifier.weight(1f).testTag("btn_open_saf_folder"),
        )
        BentoActionTile(
          title = "Instal .oiv",
          subtitle = "Pasang Mod Pack",
          icon = Icons.Rounded.InstallMobile,
          accent = AmberSecondary,
          container = AmberContainer,
          onClick = onInstallOiv,
          modifier = Modifier.weight(1f).testTag("btn_install_oiv_file"),
        )
        BentoActionTile(
          title = "Folder Mods",
          subtitle = "GTA V/mods",
          icon = Icons.Rounded.Home,
          accent = MaterialTheme.colorScheme.onSurface,
          container = ElevatedContainer,
          onClick = onWorkspaceHome,
          modifier = Modifier.weight(1f).testTag("btn_workspace_home"),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          BentoActionTile(
            title = "Buka File",
            subtitle = "XML, DDS, RPF",
            icon = Icons.Rounded.FileOpen,
            accent = EmeraldPrimary,
            container = EmeraldContainer,
            onClick = onOpenFile,
            modifier = Modifier.weight(1f).testTag("btn_open_saf_file"),
          )
          BentoActionTile(
            title = "Buka Folder",
            subtitle = "Direktori SAF",
            icon = Icons.Rounded.FolderOpen,
            accent = CyanTertiary,
            container = CyanContainer,
            onClick = onOpenFolder,
            modifier = Modifier.weight(1f).testTag("btn_open_saf_folder"),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          BentoActionTile(
            title = "Instal .oiv",
            subtitle = "Pasang Mod Pack",
            icon = Icons.Rounded.InstallMobile,
            accent = AmberSecondary,
            container = AmberContainer,
            onClick = onInstallOiv,
            modifier = Modifier.weight(1f).testTag("btn_install_oiv_file"),
          )
          BentoActionTile(
            title = "Folder Mods",
            subtitle = "GTA V/mods",
            icon = Icons.Rounded.Home,
            accent = MaterialTheme.colorScheme.onSurface,
            container = ElevatedContainer,
            onClick = onWorkspaceHome,
            modifier = Modifier.weight(1f).testTag("btn_workspace_home"),
          )
        }
      }
    }
  }
}

@Composable
private fun BentoActionTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  container: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.85f)),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(container),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun QuickShortcutsSection(
  onOpenHandlingXml: () -> Unit,
  onOpenTextureDds: () -> Unit,
  onOpenSampleOiv: () -> Unit,
  onOpenSampleRpf: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Akses Cepat",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      QuickShortcutChipCard(
        title = "sample_open.rpf",
        tag = "RPF EDITOR",
        icon = Icons.Rounded.Inventory2,
        accent = EmeraldPrimary,
        onClick = onOpenSampleRpf,
        modifier = Modifier.testTag("shortcut_sample_rpf"),
      )
      QuickShortcutChipCard(
        title = "handling.meta",
        tag = "XML EDITOR",
        icon = Icons.Rounded.Code,
        accent = EmeraldPrimary,
        onClick = onOpenHandlingXml,
        modifier = Modifier.testTag("shortcut_handling_xml"),
      )
      QuickShortcutChipCard(
        title = "adder_livery.dds",
        tag = "TEKSTUR DDS",
        icon = Icons.Rounded.Image,
        accent = CyanTertiary,
        onClick = onOpenTextureDds,
        modifier = Modifier.testTag("shortcut_livery_dds"),
      )
      QuickShortcutChipCard(
        title = "Custom_Livery.oiv",
        tag = "INSTAL OIV",
        icon = Icons.Rounded.Archive,
        accent = AmberSecondary,
        onClick = onOpenSampleOiv,
        modifier = Modifier.testTag("shortcut_sample_oiv"),
      )
    }
  }
}

@Composable
private fun QuickShortcutChipCard(
  title: String,
  tag: String,
  icon: ImageVector,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    modifier =
      modifier
        .width(156.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        FormatTagPill(text = tag, accent = accent)
      }
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun BreadcrumbRow(
  location: ExplorerLocation,
  canNavigateUp: Boolean,
  onNavigateUp: () -> Unit,
  onOpenLocation: (ExplorerLocation) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.85f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onNavigateUp,
        enabled = canNavigateUp,
        modifier = Modifier.testTag("btn_nav_up"),
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Naik satu folder",
          tint =
            if (canNavigateUp) EmeraldPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
      }

      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        when (location) {
          is ExplorerLocation.LocalDir -> {
            val parts = location.relativePath.trim('/').split('/').filter { it.isNotEmpty() }
            parts.forEachIndexed { idx, segment ->
              val targetPath = parts.subList(0, idx + 1).joinToString("/")
              Text(
                text = segment,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (idx == parts.lastIndex) FontWeight.Bold else FontWeight.Normal
                  ),
                color =
                  if (idx == parts.lastIndex) EmeraldPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenLocation(ExplorerLocation.LocalDir(targetPath)) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
              )
              if (idx < parts.lastIndex) {
                Icon(
                  Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
          is ExplorerLocation.ArchiveView -> {
            Text(
              text = location.archiveTitle,
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = AmberSecondary,
              modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                  .clickable { onOpenLocation(location.copy(innerPath = "")) }
                  .padding(horizontal = 6.dp, vertical = 6.dp),
            )
            val innerParts = location.innerPath.trim('/').split('/').filter { it.isNotEmpty() }
            innerParts.forEachIndexed { idx, seg ->
              Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              val subPath = innerParts.subList(0, idx + 1).joinToString("/")
              Text(
                text = seg,
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (idx == innerParts.lastIndex) EmeraldPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenLocation(location.copy(innerPath = subPath)) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
              )
            }
          }
          is ExplorerLocation.SafTreeDir -> {
            location.displayStack.forEachIndexed { idx, (docId, label) ->
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (idx == location.displayStack.lastIndex) CyanTertiary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable {
                      onOpenLocation(
                        location.copy(
                          documentId = docId,
                          displayStack = location.displayStack.subList(0, idx + 1),
                        )
                      )
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
              )
              if (idx < location.displayStack.lastIndex) {
                Icon(
                  Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExplorerEntryCard(
  entry: ModFileEntry,
  editMode: Boolean,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }

  val badgeColor: Color =
    when (entry.kind) {
      ModItemKind.RPF_ARCHIVE, ModItemKind.XML_META -> EmeraldPrimary
      ModItemKind.FOLDER, ModItemKind.OIV_ZIP_ARCHIVE -> AmberSecondary
      ModItemKind.YTD_DICTIONARY, ModItemKind.IMAGE_TEXTURE -> CyanTertiary
      ModItemKind.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("entry_${entry.name}"),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.85f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModFileIconBadge(kind = entry.kind, badge = entry.badge)
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          text = entry.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(8.dp))
      FormatTagPill(text = entry.badge, accent = badgeColor)

      if (editMode) {
        Box {
          IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.testTag("entry_menu_${entry.name}"),
          ) {
            Icon(
              Icons.Rounded.MoreVert,
              contentDescription = "Opsi ${entry.name}",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Ubah Nama") },
              leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
              onClick = {
                menuOpen = false
                onRename()
              },
            )
            DropdownMenuItem(
              text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Delete,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
              },
              onClick = {
                menuOpen = false
                onDelete()
              },
            )
          }
        }
      } else if (entry.kind == ModItemKind.FOLDER ||
        entry.kind == ModItemKind.YTD_DICTIONARY ||
        entry.kind == ModItemKind.RPF_ARCHIVE ||
        entry.kind == ModItemKind.OIV_ZIP_ARCHIVE
      ) {
        Spacer(Modifier.width(4.dp))
        Icon(
          Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun CreateItemDialog(
  onDismiss: () -> Unit,
  onCreateFile: (String, String) -> Unit,
  onCreateFolder: (String) -> Unit,
) {
  var isFolder by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("custom_handling.meta") }
  var template by remember { mutableStateOf("handling") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Buat Baru di Workspace") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !isFolder,
            onClick = {
              isFolder = false
              if (!name.contains('.')) name = "custom.xml"
            },
            label = { Text("File XML/Meta") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(16.dp)) },
          )
          FilterChip(
            selected = isFolder,
            onClick = {
              isFolder = true
              name = "folder_mod_baru"
            },
            label = { Text("Folder / .ytd") },
            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(16.dp)) },
          )
        }
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(if (isFolder) "Nama Folder" else "Nama File") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_item_name_input"),
        )
        if (!isFolder) {
          Text("Template GTA V:", style = MaterialTheme.typography.labelMedium)
          val options =
            listOf(
              "handling" to "handling.meta (Fisika Kendaraan)",
              "vehicles" to "vehicles.meta (Model Kendaraan)",
              "assembly" to "assembly.xml (Paket OpenIV)",
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
      }
    },
    confirmButton = {
      TextButton(
        enabled = name.isNotBlank(),
        onClick = {
          if (isFolder) onCreateFolder(name) else onCreateFile(name, template)
        },
      ) {
        Text("Buat", color = EmeraldPrimary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
  )
}

@Composable
private fun RenameEntryDialog(
  currentName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf(currentName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ubah Nama") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        label = { Text("Nama Baru") },
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
        Text("Simpan", color = EmeraldPrimary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
  )
}
