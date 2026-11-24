package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.OivInstalledRecord
import com.example.data.formatByteSize
import com.example.ui.ToolsUiState
import com.example.ui.components.FormatTagPill
import com.example.ui.components.GtaHeaderBackdrop
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ToolsScreen(
  state: ToolsUiState,
  installedOivPackages: List<OivInstalledRecord>,
  editMode: Boolean,
  workspaceStats: Pair<Int, Long>,
  onHashInputChanged: (String) -> Unit,
  onColorHexChanged: (String) -> Unit,
  onInstallOivUri: (Uri) -> Unit,
  onUninstallOiv: (String) -> Unit,
  onResetWorkspace: () -> Unit,
  onShowToast: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val clipboard = LocalClipboardManager.current
  var confirmReset by remember { mutableStateOf(false) }
  val oivPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onInstallOivUri(uri)
    }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // GTA V Tools Header Banner
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, AmberSecondary.copy(alpha = 0.3f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      GtaHeaderBackdrop(imageAlpha = 0.48f, scrimStartAlpha = 0.9f, scrimEndAlpha = 0.62f) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FormatTagPill(text = "UTILITY SUITE", accent = AmberSecondary)
          Text("Alat Modding GTA V", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Kelola paket .oiv terpasang, hitung hash JOAAT, dan konversi warna XML/Shader.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // OIV Package Manager
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Archive, contentDescription = null, tint = AmberSecondary)
            Spacer(Modifier.width(8.dp))
            Text("Manajer Paket .oiv", style = MaterialTheme.typography.titleMedium)
          }
          FormatTagPill(text = "${installedOivPackages.size} TERPASANG", accent = EmeraldPrimary)
        }
        Text(
          text = "Instal paket mod OpenIV (.oiv) dari perangkat atau kelola mod yang sudah terpasang beserta cadangannya.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
          onClick = { oivPicker.launch(arrayOf("*/*")) },
          modifier = Modifier.fillMaxWidth().testTag("tools_pick_oiv_btn"),
        ) {
          Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Pilih & Instal File .oiv")
        }

        if (installedOivPackages.isEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Belum ada paket .oiv terpasang. Ketuk file .oiv di Explorer (misal Custom_Livery_Pack.oiv) atau pilih file .oiv dari perangkat.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else {
          installedOivPackages.forEach { pkg ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.fillMaxWidth().testTag("installed_oiv_${pkg.installId}"),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pkg.name, style = MaterialTheme.typography.titleSmall)
                    FormatTagPill(text = "v${pkg.version}", accent = AmberSecondary)
                  }
                  Text(
                    text = "${pkg.targetLabel} • ${pkg.writtenRelativePaths.size} file" +
                      if (pkg.backedUpFiles.isNotEmpty()) " • ${pkg.backedUpFiles.size} backup" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { onUninstallOiv(pkg.installId) },
                  modifier = Modifier.testTag("uninstall_oiv_${pkg.installId}"),
                ) {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Copot ${pkg.name}",
                    tint = if (editMode) CoralError else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Jenkins JOAAT Hash Calculator
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Tag, contentDescription = null, tint = EmeraldPrimary)
          Spacer(Modifier.width(8.dp))
          Text("Kalkulator Hash JOAAT (GTA V)", style = MaterialTheme.typography.titleMedium)
        }
        Text(
          text = "Hitung hash Jenkins One-At-A-Time untuk nama model, senjata, atau entitas RAGE.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = state.hashInput,
          onValueChange = onHashInputChanged,
          label = { Text("Nama model / key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("tools_hash_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("adder", "zentorno", "kuruma2", "weapon_carbinerifle", "s_m_y_cop_01").forEach { preset ->
            AssistChip(
              onClick = { onHashInputChanged(preset) },
              label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Hex (uint32)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = state.hashResult.hexUpper,
                  style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = EmeraldPrimary,
                  modifier = Modifier.testTag("tools_hash_hex_output"),
                )
              }
              IconButton(
                onClick = {
                  clipboard.setText(AnnotatedString(state.hashResult.hexUpper))
                  onShowToast("Hash ${state.hashResult.hexUpper} disalin")
                }
              ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Salin Hex Hash")
              }
            }
            Text(
              text = "Unsigned: ${state.hashResult.unsignedInt}  •  Signed: ${state.hashResult.signedInt}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = CyanTertiary,
            )
          }
        }
      }
    }

    // Color & Float RGB Converter
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Palette, contentDescription = null, tint = AmberSecondary)
          Spacer(Modifier.width(8.dp))
          Text("Konverter Warna XML & Shader", style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = state.colorHexInput,
            onValueChange = onColorHexChanged,
            label = { Text("Kode Hex (#RRGGBB)") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("tools_color_input"),
          )
          val previewColor =
            state.colorBreakdown?.let { Color(it.rInt, it.gInt, it.bInt) }
              ?: MaterialTheme.colorScheme.surfaceContainer
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(previewColor)
                .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
          )
        }
        state.colorBreakdown?.let { c ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "RGB: (${c.rInt}, ${c.gInt}, ${c.bInt})  •  ABGR: ${c.abgrHex}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "<vec3 x=\"${c.rFloat}\" y=\"${c.gFloat}\" z=\"${c.bFloat}\" />",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = EmeraldPrimary,
              )
            }
          }
        }
      }
    }

    // Workspace Storage & Format Guide
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Storage, contentDescription = null, tint = CyanTertiary)
          Spacer(Modifier.width(8.dp))
          Text("Workspace & Format Didukung", style = MaterialTheme.typography.titleMedium)
        }
        Text(
          text = "Workspace GTA V Lokal: ${workspaceStats.first} file • ${formatByteSize(workspaceStats.second)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("RPF7 OPEN", "OIV / ZIP", "XML / META", "DDS DXT1/3/5", "TGA", "PNG / WEBP").forEach { tag ->
            FormatTagPill(text = tag, accent = CyanTertiary)
          }
        }
        OutlinedButton(
          onClick = { confirmReset = true },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberSecondary),
          modifier = Modifier.fillMaxWidth().testTag("tools_reset_workspace_btn"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Reset Workspace Sample GTA V")
        }
      }
    }
  }

  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text("Reset Workspace Sample?") },
      text = { Text("Semua file sample GTA V (handling.meta, vehicles.meta, .oiv, .rpf, .ytd) akan dikembalikan ke kondisi awal.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmReset = false
            onResetWorkspace()
          }
        ) {
          Text("Reset", color = AmberSecondary)
        }
      },
      dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } },
    )
  }
}
