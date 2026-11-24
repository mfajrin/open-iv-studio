package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.XmlSyntaxVisualTransformation
import com.example.core.XmlTreeNode
import com.example.ui.XmlEditorUiState
import com.example.ui.components.FormatTagPill
import com.example.ui.components.GtaHeaderBackdrop
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralOnContainer
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SyntaxColors

@Composable
fun XmlEditorScreen(
  state: XmlEditorUiState,
  editMode: Boolean,
  recentPaths: List<String>,
  onTextChanged: (TextFieldValue) -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onValidate: () -> Unit,
  onFormat: () -> Unit,
  onSave: () -> Unit,
  onInsertSnippet: (String) -> Unit,
  onToggleTreeMode: (Boolean) -> Unit,
  onToggleFindBar: () -> Unit,
  onFindQueryChanged: (String) -> Unit,
  onReplaceQueryChanged: (String) -> Unit,
  onFindNext: () -> Unit,
  onReplaceAll: () -> Unit,
  onToggleWordWrap: () -> Unit,
  onToggleLineNumbers: () -> Unit,
  onOpenSampleRelative: (String) -> Unit,
  onOpenSafFile: (Uri) -> Unit,
  modifier: Modifier = Modifier,
) {
  val openXmlLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onOpenSafFile(uri)
    }

  Column(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
  ) {
    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EmeraldPrimary)
      }
      return
    }

    if (state.source == null) {
      XmlEmptyStartPanel(
        recentPaths = recentPaths,
        onOpenDeviceFile = { openXmlLauncher.launch(arrayOf("*/*")) },
        onOpenSampleRelative = onOpenSampleRelative,
      )
      return
    }

    // Document Header Bar with GTA V Backdrop
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      GtaHeaderBackdrop(imageAlpha = 0.4f, scrimStartAlpha = 0.88f, scrimEndAlpha = 0.68f) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = state.fileName + if (state.isDirty) " •" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isDirty) AmberSecondary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (state.isReadOnlyArchive) {
                Spacer(Modifier.width(6.dp))
                FormatTagPill(text = "ARSIP", accent = AmberSecondary)
              }
            }
            val linesCount = remember(state.textValue.text) { state.textValue.text.count { it == '\n' } + 1 }
            Text(
              text = "$linesCount baris • ${state.textValue.text.length} karakter",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // Mode Switch: Kode | Pohon XML
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = !state.isTreeMode,
              onClick = { onToggleTreeMode(false) },
              label = { Text("Kode", style = MaterialTheme.typography.labelMedium) },
              leadingIcon = { Icon(Icons.Rounded.Code, null, Modifier.size(15.dp)) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = EmeraldContainer,
                  selectedLabelColor = EmeraldOnContainer,
                ),
              modifier = Modifier.testTag("xml_mode_code"),
            )
            FilterChip(
              selected = state.isTreeMode,
              onClick = { onToggleTreeMode(true) },
              label = { Text("Pohon", style = MaterialTheme.typography.labelMedium) },
              leadingIcon = { Icon(Icons.Rounded.AccountTree, null, Modifier.size(15.dp)) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = EmeraldContainer,
                  selectedLabelColor = EmeraldOnContainer,
                ),
              modifier = Modifier.testTag("xml_mode_tree"),
            )
          }
        }
      }
    }

    // Editor Action Toolbar
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        onClick = onSave,
        enabled = editMode,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (state.isDirty) AmberSecondary else EmeraldPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.height(36.dp).testTag("xml_save_button"),
      ) {
        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
          text = if (state.isReadOnlyArchive) "Ekstrak" else "Simpan",
          style = MaterialTheme.typography.labelMedium,
        )
      }

      IconButton(
        onClick = onUndo,
        enabled = editMode && state.undoStack.isNotEmpty(),
        modifier = Modifier.testTag("xml_undo_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
      }
      IconButton(
        onClick = onRedo,
        enabled = editMode && state.redoStack.isNotEmpty(),
        modifier = Modifier.testTag("xml_redo_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
      }

      AssistChip(
        onClick = onFormat,
        label = { Text("Rapikan") },
        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(15.dp)) },
        modifier = Modifier.testTag("xml_format_button"),
      )
      AssistChip(
        onClick = onValidate,
        label = { Text("Validasi") },
        leadingIcon = { Icon(Icons.Rounded.Verified, null, Modifier.size(15.dp)) },
        modifier = Modifier.testTag("xml_validate_button"),
      )
      AssistChip(
        onClick = onToggleFindBar,
        label = { Text("Cari") },
        leadingIcon = { Icon(Icons.Rounded.FindReplace, null, Modifier.size(15.dp)) },
        modifier = Modifier.testTag("xml_find_button"),
      )
      AssistChip(
        onClick = onToggleWordWrap,
        label = { Text(if (state.wordWrap) "Wrap: ON" else "Wrap: OFF") },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.WrapText, null, Modifier.size(15.dp)) },
      )
      AssistChip(
        onClick = onToggleLineNumbers,
        label = { Text("No. Baris") },
        leadingIcon = { Icon(Icons.Rounded.FormatListNumbered, null, Modifier.size(15.dp)) },
      )
      AssistChip(
        onClick = { openXmlLauncher.launch(arrayOf("*/*")) },
        label = { Text("Buka Lain") },
        leadingIcon = { Icon(Icons.Rounded.FileOpen, null, Modifier.size(15.dp)) },
      )
    }

    // Find & Replace Bar
    AnimatedVisibility(visible = state.showFindBar && !state.isTreeMode) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, HairlineBorder),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = state.findQuery,
              onValueChange = onFindQueryChanged,
              label = { Text("Cari teks") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("xml_find_input"),
            )
            OutlinedTextField(
              value = state.replaceQuery,
              onValueChange = onReplaceQueryChanged,
              label = { Text("Ganti dengan") },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("xml_replace_input"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onFindNext, modifier = Modifier.testTag("xml_find_next_btn")) {
              Text("Cari Berikutnya", color = CyanTertiary)
            }
            TextButton(
              onClick = onReplaceAll,
              enabled = editMode,
              modifier = Modifier.testTag("xml_replace_all_btn"),
            ) {
              Text("Ganti Semua", color = AmberSecondary)
            }
          }
        }
      }
    }

    // Validation Status Banner
    state.validationResult?.let { res ->
      val bg = if (res.isValid) EmeraldContainer.copy(alpha = 0.65f) else CoralContainer
      val fg = if (res.isValid) EmeraldOnContainer else CoralOnContainer
      val iconTint = if (res.isValid) EmeraldPrimary else CoralError
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("xml_validation_banner"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = if (res.isValid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        val lineDetail = if (res.line != null) "Baris ${res.line}, Kolom ${res.column}: " else ""
        Text(
          text = lineDetail + res.message,
          style = MaterialTheme.typography.labelSmall,
          color = fg,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Main Content: Code Editor or XML Tree Inspector
    if (state.isTreeMode) {
      XmlTreeInspectorView(
        treeNode = state.treeNode,
        modifier = Modifier.weight(1f),
      )
    } else {
      XmlCodeEditorCanvas(
        textValue = state.textValue,
        onTextChanged = onTextChanged,
        readOnly = !editMode,
        wordWrap = state.wordWrap,
        showLineNumbers = state.showLineNumbers,
        findQuery = state.findQuery,
        modifier = Modifier.weight(1f),
      )

      // Quick GTA V Snippet Insertion Strip
      if (editMode) {
        val snippets =
          listOf(
            "<Item>" to "<Item>\n  \n</Item>",
            "value=\"\"" to "value=\"\"",
            "<fMass />" to "<fMass value=\"1500.000000\" />",
            "<vec3 />" to "<vec3 x=\"0.000000\" y=\"0.000000\" z=\"0.000000\" />",
            "<!-- -->" to "<!-- catatan -->",
          )
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          snippets.forEach { (label, snippet) ->
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, HairlineBorder),
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onInsertSnippet(snippet) },
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun XmlCodeEditorCanvas(
  textValue: TextFieldValue,
  onTextChanged: (TextFieldValue) -> Unit,
  readOnly: Boolean,
  wordWrap: Boolean,
  showLineNumbers: Boolean,
  findQuery: String,
  modifier: Modifier = Modifier,
) {
  val verticalScroll = rememberScrollState()
  val horizontalScroll = rememberScrollState()
  var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  val density = LocalDensity.current

  val visualTransformation =
    remember(findQuery) { XmlSyntaxVisualTransformation(searchQuery = findQuery) }

  val lineStartOffsets =
    remember(textValue.text) {
      val list = mutableListOf(0)
      textValue.text.forEachIndexed { idx, ch ->
        if (ch == '\n') list.add(idx + 1)
      }
      list
    }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(verticalScroll),
  ) {
    if (showLineNumbers) {
      Box(
        modifier =
          Modifier.width(46.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 12.dp, bottom = 36.dp, end = 6.dp),
      ) {
        val layout = textLayoutResult
        if (layout != null) {
          lineStartOffsets.forEachIndexed { lineIdx, charOffset ->
            val safeOff = charOffset.coerceIn(0, textValue.text.length)
            val visualLine = layout.getLineForOffset(safeOff)
            val topPx = layout.getLineTop(visualLine)
            val topDp = with(density) { topPx.toDp() }
            Text(
              text = "${lineIdx + 1}",
              style =
                TextStyle(
                  fontFamily = JetBrainsMonoFontFamily,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
              modifier =
                Modifier.align(Alignment.TopEnd).padding(top = topDp),
            )
          }
        } else {
          Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
            lineStartOffsets.indices.forEach { idx ->
              Text(
                text = "${idx + 1}",
                style =
                  TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                  ),
              )
            }
          }
        }
      }
      Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(HairlineBorder))
    }

    val fieldModifier =
      Modifier.weight(1f)
        .then(if (!wordWrap) Modifier.horizontalScroll(horizontalScroll) else Modifier)
        .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 48.dp)
        .testTag("xml_code_textfield")

    BasicTextField(
      value = textValue,
      onValueChange = onTextChanged,
      readOnly = readOnly,
      visualTransformation = visualTransformation,
      onTextLayout = { textLayoutResult = it },
      cursorBrush = SolidColor(if (readOnly) AmberSecondary else EmeraldPrimary),
      textStyle =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontSize = 13.sp,
          lineHeight = 20.sp,
          color = SyntaxColors.PlainText,
        ),
      modifier = fieldModifier,
    )
  }
}

@Composable
private fun XmlTreeInspectorView(
  treeNode: XmlTreeNode?,
  modifier: Modifier = Modifier,
) {
  if (treeNode == null) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      Text(
        text = "Pohon XML tidak tersedia. Perbaiki kesalahan sintaks XML terlebih dahulu.",
        style = MaterialTheme.typography.bodyMedium,
        color = CoralError,
      )
    }
    return
  }

  val expandedMap = remember(treeNode) { mutableStateMapOf(treeNode.id to true) }

  fun flattenVisible(node: XmlTreeNode): List<XmlTreeNode> {
    val result = mutableListOf(node)
    if (expandedMap[node.id] == true) {
      node.children.forEach { child -> result.addAll(flattenVisible(child)) }
    }
    return result
  }

  val visibleNodes = flattenVisible(treeNode)

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("xml_tree_list"),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(visibleNodes, key = { it.id }) { node ->
      val hasChildren = node.children.isNotEmpty()
      val isExpanded = expandedMap[node.id] == true
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .padding(start = (node.depth * 14).dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = hasChildren) { expandedMap[node.id] = !isExpanded },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (hasChildren) {
            Icon(
              imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription = if (isExpanded) "Tutup node" else "Buka node",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
          } else {
            Spacer(Modifier.width(24.dp))
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "<${node.tagName}>",
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily,
                  ),
                color = EmeraldPrimary,
              )
              if (hasChildren) {
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "${node.children.size} anak",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (node.attributes.isNotEmpty()) {
              Text(
                text = node.attributes.joinToString(" ") { "${it.first}=\"${it.second}\"" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = AmberSecondary,
              )
            }
            node.textValue?.let { v ->
              Text(
                text = v,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = CyanTertiary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun XmlEmptyStartPanel(
  recentPaths: List<String>,
  onOpenDeviceFile: () -> Unit,
  onOpenSampleRelative: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      GtaHeaderBackdrop(imageAlpha = 0.45f, scrimStartAlpha = 0.92f, scrimEndAlpha = 0.7f) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(
            Icons.Rounded.Code,
            contentDescription = null,
            tint = EmeraldPrimary,
            modifier = Modifier.size(36.dp),
          )
          Text("XML & GTA Meta Editor", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Edit file handling.meta, vehicles.meta, carvariations.meta, assembly.xml, atau dokumen XML dari perangkat dengan pewarnaan sintaks dan validator DOM.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = onOpenDeviceFile,
            modifier = Modifier.fillMaxWidth().testTag("xml_empty_open_file"),
          ) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buka File XML / Meta dari Perangkat")
          }
        }
      }
    }

    Text("Buka Cepat Sample GTA V", style = MaterialTheme.typography.titleMedium)
    val quickSamples =
      listOf(
        "GTA V/update/common/data/handling.meta" to "handling.meta (Fisika Adder & Zentorno)",
        "GTA V/update/common/data/levels/gta5/vehicles.meta" to "vehicles.meta (Data Kendaraan)",
        "GTA V/update/common/data/carvariations.meta" to "carvariations.meta (Warna & Modkit)",
        "GTA V/update/common/data/visualsettings.xml" to "visualsettings.xml (Shader & Lampu)",
      )
    quickSamples.forEach { (relPath, title) ->
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenSampleRelative(relPath) }
            .testTag("xml_quick_${relPath.substringAfterLast('/')}"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(title, style = MaterialTheme.typography.bodyMedium)
          FormatTagPill(text = "BUKA", accent = EmeraldPrimary)
        }
      }
    }

    if (recentPaths.isNotEmpty()) {
      Text("Terakhir Dibuka", style = MaterialTheme.typography.titleMedium)
      recentPaths.forEach { path ->
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(10.dp),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable { onOpenSampleRelative(path) },
        ) {
          Text(
            text = path,
            style = MaterialTheme.typography.labelMedium,
            color = CyanTertiary,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }
  }
}
