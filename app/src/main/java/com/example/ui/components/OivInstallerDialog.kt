package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.OivManifest
import com.example.core.OivOperation
import com.example.data.BinarySource
import com.example.data.OivInstallTarget
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily

data class OivInstallerUiState(
  val isVisible: Boolean = false,
  val fileName: String = "",
  val source: BinarySource? = null,
  val isParsing: Boolean = false,
  val parseError: String? = null,
  val manifest: OivManifest? = null,
  val isInstalling: Boolean = false,
  val progress: Float = 0f,
  val installLogs: List<String> = emptyList(),
  val isFinished: Boolean = false,
  val installedTarget: OivInstallTarget? = null,
)

@Composable
fun OivInstallerDialog(
  state: OivInstallerUiState,
  editMode: Boolean,
  onEnableEditMode: () -> Unit,
  onDismiss: () -> Unit,
  onBrowseArchiveContents: () -> Unit,
  onViewAssemblyXml: () -> Unit,
  onInstall: (target: OivInstallTarget, createBackup: Boolean) -> Unit,
  onOpenInstalledLocation: () -> Unit,
) {
  if (!state.isVisible) return

  var selectedTargetIdx by remember(state.fileName) { mutableIntStateOf(0) }
  var safTreeUri by remember(state.fileName) { mutableStateOf<Uri?>(null) }
  var createBackup by remember(state.fileName) { mutableStateOf(true) }

  val safFolderPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        safTreeUri = uri
        selectedTargetIdx = 2
      }
    }

  Dialog(
    onDismissRequest = { if (!state.isInstalling) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.94f)
          .heightIn(max = 680.dp)
          .testTag("oiv_installer_dialog"),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, HairlineBorder),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Header Bar with GTA V Backdrop
        GtaHeaderBackdrop(imageAlpha = 0.45f, scrimStartAlpha = 0.9f, scrimEndAlpha = 0.65f) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                Icons.Rounded.Archive,
                contentDescription = null,
                tint = AmberSecondary,
                modifier = Modifier.size(24.dp),
              )
              Spacer(Modifier.width(10.dp))
              Column {
                Text(
                  text = "OIV Package Installer",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = state.fileName,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              enabled = !state.isInstalling,
              modifier = Modifier.testTag("oiv_close_button"),
            ) {
              Icon(Icons.Rounded.Close, contentDescription = "Tutup installer")
            }
          }
        }

        // Body
        Column(
          modifier =
            Modifier.weight(1f, fill = false)
              .verticalScroll(rememberScrollState())
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          when {
            state.isParsing -> {
              Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  CircularProgressIndicator(color = AmberSecondary)
                  Spacer(Modifier.height(10.dp))
                  Text("Membaca assembly.xml...", style = MaterialTheme.typography.bodySmall)
                }
              }
            }
            state.parseError != null -> {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    text = state.parseError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                  OutlinedButton(onClick = onBrowseArchiveContents) {
                    Text("Jelajahi Isi Arsip Secara Manual")
                  }
                }
              }
            }
            state.manifest != null -> {
              val m = state.manifest
              // Metadata Card
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = m.metadata.name,
                      style = MaterialTheme.typography.titleMedium,
                      color = EmeraldPrimary,
                      modifier = Modifier.weight(1f),
                    )
                    FormatTagPill(text = "v${m.metadata.version}", accent = AmberSecondary)
                  }
                  Text(
                    text = "Penulis: ${m.metadata.author} • Target: ${m.metadata.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanTertiary,
                  )
                  Text(
                    text = m.metadata.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    OutlinedButton(
                      onClick = onViewAssemblyXml,
                      modifier = Modifier.weight(1f).testTag("oiv_view_assembly_btn"),
                    ) {
                      Icon(Icons.Rounded.Code, null, Modifier.size(15.dp))
                      Spacer(Modifier.width(5.dp))
                      Text("assembly.xml", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                      onClick = onBrowseArchiveContents,
                      modifier = Modifier.weight(1f).testTag("oiv_browse_zip_btn"),
                    ) {
                      Icon(Icons.Rounded.FolderOpen, null, Modifier.size(15.dp))
                      Spacer(Modifier.width(5.dp))
                      Text("Isi Arsip", style = MaterialTheme.typography.labelMedium)
                    }
                  }
                }
              }

              // Operations List
              Text(
                text = "Operasi Instalasi (${m.operations.size})",
                style = MaterialTheme.typography.labelLarge,
              )
              Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  m.operations.take(12).forEach { op ->
                    when (op) {
                      is OivOperation.CreateArchive ->
                        OperationRow(badge = "ARSIP", color = AmberSecondary, text = op.archivePath)
                      is OivOperation.AddFile -> {
                        val targetFull =
                          if (op.archivePath.isNullOrBlank()) op.destinationPath
                          else "${op.archivePath}/${op.destinationPath}"
                        OperationRow(badge = "+ TAMBAH", color = EmeraldPrimary, text = targetFull)
                      }
                      is OivOperation.DeleteFile -> {
                        val targetFull =
                          if (op.archivePath.isNullOrBlank()) op.destinationPath
                          else "${op.archivePath}/${op.destinationPath}"
                        OperationRow(badge = "- HAPUS", color = CoralError, text = targetFull)
                      }
                    }
                  }
                  if (m.operations.size > 12) {
                    Text(
                      text = "...dan ${m.operations.size - 12} operasi lainnya",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              if (!state.isInstalling && !state.isFinished) {
                // Edit Mode Prompt if disabled
                if (!editMode) {
                  Surface(
                    color = AmberContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberSecondary),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(
                      modifier = Modifier.padding(10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Security, null, tint = AmberSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                          text = "Edit Mode nonaktif. Aktifkan untuk menginstal mod.",
                          style = MaterialTheme.typography.bodySmall,
                          color = AmberOnContainer,
                        )
                      }
                      TextButton(onClick = onEnableEditMode, modifier = Modifier.testTag("oiv_enable_edit_btn")) {
                        Text("Aktifkan", color = AmberSecondary, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }

                // Target selector
                Text("Pilih Lokasi Instalasi", style = MaterialTheme.typography.labelLarge)
                TargetRadioOption(
                  selected = selectedTargetIdx == 0,
                  title = "Folder 'mods' (Direkomendasikan)",
                  subtitle = "Pasang terisolasi di GTA V/mods/ tanpa merusak file asli",
                  onClick = { selectedTargetIdx = 0 },
                  modifier = Modifier.testTag("oiv_target_mods"),
                )
                TargetRadioOption(
                  selected = selectedTargetIdx == 1,
                  title = "Workspace GTA V Utama",
                  subtitle = "Pasang/timpa langsung ke direktori GTA V/",
                  onClick = { selectedTargetIdx = 1 },
                  modifier = Modifier.testTag("oiv_target_main"),
                )
                TargetRadioOption(
                  selected = selectedTargetIdx == 2,
                  title = "Folder Perangkat (SAF)",
                  subtitle =
                    safTreeUri?.lastPathSegment?.substringAfterLast(':')
                      ?: "Pilih folder penyimpanan di perangkat Android",
                  onClick = { safFolderPicker.launch(null) },
                  modifier = Modifier.testTag("oiv_target_saf"),
                )

                if (selectedTargetIdx != 2) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { createBackup = !createBackup },
                  ) {
                    Checkbox(
                      checked = createBackup,
                      onCheckedChange = { createBackup = it },
                      colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary),
                    )
                    Text(
                      text = "Buat cadangan (backup) otomatis sebelum menimpa file",
                      style = MaterialTheme.typography.bodySmall,
                    )
                  }
                }
              }

              // Progress & Logs
              if (state.isInstalling || state.installLogs.isNotEmpty()) {
                LinearProgressIndicator(
                  progress = { state.progress },
                  color = if (state.isFinished) EmeraldPrimary else AmberSecondary,
                  modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Surface(
                  color = MaterialTheme.colorScheme.background,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, HairlineBorder),
                  modifier = Modifier.fillMaxWidth().testTag("oiv_install_log_box"),
                ) {
                  Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    state.installLogs.forEach { line ->
                      Text(
                        text = line,
                        style =
                          MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                          ),
                        color =
                          when {
                            line.startsWith("[GAGAL]") || line.startsWith("[SAF GAGAL]") -> CoralError
                            line.startsWith("[OK]") || line.startsWith("[SAF OK]") || line.startsWith("Selesai") -> EmeraldPrimary
                            line.startsWith("[BACKUP]") -> AmberSecondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                          },
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Bottom Action Bar
        if (state.manifest != null && !state.isParsing && state.parseError == null) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (state.isFinished) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                  "Instalasi Berhasil",
                  style = MaterialTheme.typography.labelLarge,
                  color = EmeraldPrimary,
                )
              }
              Button(
                onClick = onOpenInstalledLocation,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("oiv_open_result_btn"),
              ) {
                Text("Lihat Hasil")
              }
            } else {
              TextButton(onClick = onDismiss, enabled = !state.isInstalling) {
                Text("Batal")
              }
              Spacer(Modifier.width(8.dp))
              val canInstall =
                editMode && !state.isInstalling && (selectedTargetIdx != 2 || safTreeUri != null)
              Button(
                onClick = {
                  val target =
                    when (selectedTargetIdx) {
                      0 -> OivInstallTarget.ModsFolder
                      1 -> OivInstallTarget.MainWorkspace
                      else ->
                        OivInstallTarget.SafFolder(
                          treeUri = safTreeUri!!,
                          folderLabel = safTreeUri?.lastPathSegment?.substringAfterLast(':') ?: "Folder SAF",
                        )
                    }
                  onInstall(target, createBackup)
                },
                enabled = canInstall,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.testTag("oiv_install_confirm_btn"),
              ) {
                Icon(Icons.Rounded.InstallMobile, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.isInstalling) "Menginstal..." else "Instal Sekarang")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OperationRow(
  badge: String,
  color: androidx.compose.ui.graphics.Color,
  text: String,
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FormatTagPill(text = badge, accent = color)
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun TargetRadioOption(
  selected: Boolean,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = if (selected) EmeraldContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, if (selected) EmeraldPrimary else HairlineBorder),
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(
        selected = selected,
        onClick = onClick,
        colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
          subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
