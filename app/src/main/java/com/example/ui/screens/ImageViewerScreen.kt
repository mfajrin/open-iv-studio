package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.BinarySource
import com.example.data.formatByteSize
import com.example.ui.ImageViewerUiState
import com.example.ui.TextureChannel
import com.example.ui.ViewerBgMode
import com.example.ui.components.FormatTagPill
import com.example.ui.components.GtaHeaderBackdrop
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanOnContainer
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder
import java.util.Locale

@Composable
fun ImageViewerScreen(
  state: ImageViewerUiState,
  onSelectChannel: (TextureChannel) -> Unit,
  onCycleBackground: () -> Unit,
  onToggleGrid: () -> Unit,
  onRotate90: () -> Unit,
  onSamplePixelNormalized: (Float, Float) -> Unit,
  onSelectSibling: (String, BinarySource) -> Unit,
  onOpenSampleRelative: (String) -> Unit,
  onOpenSafFile: (Uri) -> Unit,
  onExportPngToUri: (Uri) -> Unit,
  modifier: Modifier = Modifier,
) {
  val openTextureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onOpenSafFile(uri)
    }
  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) onOpenSafFile(uri)
    }
  val exportPngLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
      if (uri != null) onExportPngToUri(uri)
    }

  Column(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
  ) {
    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CyanTertiary)
      }
      return
    }

    if (state.decoded == null && state.errorMessage == null) {
      ImageEmptyStartPanel(
        onPickPhoto = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onOpenDdsOrTga = { openTextureLauncher.launch(arrayOf("*/*")) },
        onOpenSampleRelative = onOpenSampleRelative,
      )
      return
    }

    val decoded = state.decoded
    if (decoded == null) {
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = state.errorMessage ?: "Gagal memuat gambar",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedButton(onClick = { openTextureLauncher.launch(arrayOf("*/*")) }) {
            Text("Pilih Gambar Lain")
          }
        }
      }
      return
    }

    var scale by remember(state.fileName) { mutableFloatStateOf(1f) }
    var offset by remember(state.fileName) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // Top Texture Info & Action Bar with GTA V Backdrop
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      GtaHeaderBackdrop(imageAlpha = 0.38f, scrimStartAlpha = 0.88f, scrimEndAlpha = 0.7f) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = state.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${decoded.width} × ${decoded.height} px • ${decoded.formatLabel} • ${formatByteSize(decoded.byteSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = CyanTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            FormatTagPill(
              text = "${(scale * 100).toInt()}%",
              accent = EmeraldPrimary,
            )
          }

          Spacer(Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AssistChip(
              onClick = {
                scale = 1f
                offset = Offset.Zero
              },
              label = { Text("Reset Zoom") },
              leadingIcon = { Icon(Icons.Rounded.ZoomOutMap, null, Modifier.size(15.dp)) },
              modifier = Modifier.testTag("img_reset_zoom"),
            )
            AssistChip(
              onClick = onRotate90,
              label = { Text("Putar 90°") },
              leadingIcon = { Icon(Icons.AutoMirrored.Rounded.RotateRight, null, Modifier.size(15.dp)) },
              modifier = Modifier.testTag("img_rotate_button"),
            )
            AssistChip(
              onClick = onCycleBackground,
              label = { Text(state.bgMode.label) },
              leadingIcon = { Icon(Icons.Rounded.Contrast, null, Modifier.size(15.dp)) },
              modifier = Modifier.testTag("img_bg_button"),
            )
            AssistChip(
              onClick = onToggleGrid,
              label = { Text(if (state.showGridOverlay) "Grid: ON" else "Grid: OFF") },
              leadingIcon = { Icon(Icons.Rounded.GridOn, null, Modifier.size(15.dp)) },
              modifier = Modifier.testTag("img_grid_button"),
            )
            AssistChip(
              onClick = {
                val base = state.fileName.substringBeforeLast('.').ifBlank { "texture" }
                exportPngLauncher.launch("${base}_export.png")
              },
              label = { Text("Ekspor PNG") },
              leadingIcon = { Icon(Icons.Rounded.Download, null, Modifier.size(15.dp)) },
              modifier = Modifier.testTag("img_export_png"),
            )
            AssistChip(
              onClick = { openTextureLauncher.launch(arrayOf("*/*")) },
              label = { Text("Buka Lain") },
              leadingIcon = { Icon(Icons.Rounded.FileOpen, null, Modifier.size(15.dp)) },
            )
          }
        }
      }
    }

    // Interactive Zoom/Pan Canvas
    Box(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .clipToBounds()
          .onSizeChanged { boxSize = it }
          .pointerInput(state.fileName) {
            detectTransformGestures { _, pan, zoom, _ ->
              val newScale = (scale * zoom).coerceIn(0.5f, 8f)
              scale = newScale
              offset = if (newScale <= 1f) Offset.Zero else offset + pan
            }
          }
          .pointerInput(state.fileName, boxSize, decoded.width, decoded.height) {
            detectTapGestures(
              onDoubleTap = {
                if (scale > 1.2f) {
                  scale = 1f
                  offset = Offset.Zero
                } else {
                  scale = 2.5f
                }
              },
              onTap = { tapPos ->
                if (boxSize.width > 0 && boxSize.height > 0) {
                  val nx = (tapPos.x / boxSize.width.toFloat()).coerceIn(0f, 1f)
                  val ny = (tapPos.y / boxSize.height.toFloat()).coerceIn(0f, 1f)
                  onSamplePixelNormalized(nx, ny)
                }
              },
            )
          }
          .testTag("image_viewer_canvas"),
      contentAlignment = Alignment.Center,
    ) {
      // Background pattern
      when (state.bgMode) {
        ViewerBgMode.CHECKERBOARD -> CheckerboardCanvas(modifier = Modifier.fillMaxSize())
        ViewerBgMode.DARK -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0D12)))
        ViewerBgMode.LIGHT -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE9EEF5)))
      }

      val colorFilter = remember(state.selectedChannel) { channelColorFilter(state.selectedChannel) }
      val imgBitmap = remember(decoded.bitmap) { decoded.bitmap.asImageBitmap() }

      Image(
        bitmap = imgBitmap,
        contentDescription = "Pratinjau tekstur ${state.fileName}",
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
        modifier =
          Modifier.fillMaxSize()
            .padding(16.dp)
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
              translationX = offset.x
              translationY = offset.y
              rotationZ = state.rotationDegrees
            },
      )

      if (state.showGridOverlay) {
        UvGridOverlay(modifier = Modifier.fillMaxSize())
      }
    }

    // Channel Filter Strip (RGB / R / G / B / Alpha)
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Kanal:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextureChannel.entries.forEach { ch ->
        FilterChip(
          selected = state.selectedChannel == ch,
          onClick = { onSelectChannel(ch) },
          label = { Text(ch.label, style = MaterialTheme.typography.labelMedium) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = CyanContainer,
              selectedLabelColor = CyanOnContainer,
            ),
          modifier = Modifier.testTag("channel_${ch.name.lowercase()}"),
        )
      }
    }

    // Sibling / YTD Dictionary Switcher Strip
    if (state.siblings.size > 1) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "YTD (${state.siblings.size}):",
          style = MaterialTheme.typography.labelSmall,
          color = AmberSecondary,
        )
        state.siblings.forEach { sib ->
          val active = sib.name == state.fileName
          Surface(
            color = if (active) EmeraldContainer else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (active) EmeraldPrimary else HairlineBorder),
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { onSelectSibling(sib.name, sib.source) },
          ) {
            Text(
              text = sib.name,
              style = MaterialTheme.typography.labelSmall,
              color = if (active) EmeraldOnContainer else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    }

    // Pixel Sample & Dominant Palette Inspector Strip
    Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
      border = BorderStroke(1.dp, HairlineBorder),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val sample = state.sampledPixel
        if (sample != null) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier =
                Modifier.size(26.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color(sample.argb))
                  .border(1.dp, HairlineBorder, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column {
              Text(
                text = "${sample.hex} • (${sample.x}, ${sample.y})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = sample.rgbaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        if (decoded.palette.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            decoded.palette.forEach { col ->
              Box(
                modifier =
                  Modifier.size(18.dp)
                    .clip(CircleShape)
                    .background(Color(col))
                    .border(1.dp, HairlineBorder, CircleShape)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CheckerboardCanvas(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val tile = 16.dp.toPx()
    val cols = (size.width / tile).toInt() + 1
    val rows = (size.height / tile).toInt() + 1
    val c1 = Color(0xFF1A222C)
    val c2 = Color(0xFF232E3B)
    for (r in 0 until rows) {
      for (c in 0 until cols) {
        drawRect(
          color = if ((r + c) % 2 == 0) c1 else c2,
          topLeft = Offset(c * tile, r * tile),
          size = Size(tile, tile),
        )
      }
    }
  }
}

@Composable
private fun UvGridOverlay(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val divisions = 8
    val stepX = size.width / divisions
    val stepY = size.height / divisions
    val lineColor = EmeraldPrimary.copy(alpha = 0.35f)
    for (i in 1 until divisions) {
      drawLine(lineColor, Offset(i * stepX, 0f), Offset(i * stepX, size.height), strokeWidth = 1f)
      drawLine(lineColor, Offset(0f, i * stepY), Offset(size.width, i * stepY), strokeWidth = 1f)
    }
  }
}

private fun channelColorFilter(channel: TextureChannel): ColorFilter? {
  return when (channel) {
    TextureChannel.RGB -> null
    TextureChannel.RED ->
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      )
    TextureChannel.GREEN ->
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      )
    TextureChannel.BLUE ->
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      )
    TextureChannel.ALPHA ->
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 0f, 255f,
          )
        )
      )
  }
}

@Composable
private fun ImageEmptyStartPanel(
  onPickPhoto: () -> Unit,
  onOpenDdsOrTga: () -> Unit,
  onOpenSampleRelative: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, CyanTertiary.copy(alpha = 0.28f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      GtaHeaderBackdrop(imageAlpha = 0.45f, scrimStartAlpha = 0.92f, scrimEndAlpha = 0.7f) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(
            Icons.Rounded.Image,
            contentDescription = null,
            tint = CyanTertiary,
            modifier = Modifier.size(36.dp),
          )
          Text("Penampil Tekstur & Gambar", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Inspeksi tekstur mod GTA V (.dds DXT1/DXT3/DXT5/ARGB, .tga, .png, .jpg, .webp) dengan zoom presisi, kanal RGBA/Alpha, latar transparan, dan sampel warna piksel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = onOpenDdsOrTga,
              modifier = Modifier.weight(1f).testTag("img_empty_open_dds"),
            ) {
              Icon(Icons.Rounded.FileOpen, null, Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Buka DDS/TGA")
            }
            OutlinedButton(
              onClick = onPickPhoto,
              modifier = Modifier.weight(1f).testTag("img_empty_pick_photo"),
            ) {
              Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Dari Galeri")
            }
          }
        }
      }
    }

    Text("Buka Cepat Tekstur Sample (.ytd)", style = MaterialTheme.typography.titleMedium)
    val samples =
      listOf(
        "GTA V/update/x64/textures/vehshare_livery.ytd/adder_carbon_livery.dds" to
          "adder_carbon_livery.dds (DDS 32-bit Livery)",
        "GTA V/update/x64/textures/vehshare_livery.ytd/adder_carbon_livery.png" to
          "adder_carbon_livery.png (PNG Texture)",
        "GTA V/update/x64/textures/hud_minimap.ytd/radar_downtown_ls.dds" to
          "radar_downtown_ls.dds (Minimap Radar DDS)",
      )
    samples.forEach { (rel, label) ->
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenSampleRelative(rel) }
            .testTag("img_quick_${rel.substringAfterLast('/')}"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          FormatTagPill(
            text = rel.substringAfterLast('.').uppercase(Locale.US),
            accent = CyanTertiary,
          )
        }
      }
    }
  }
}
