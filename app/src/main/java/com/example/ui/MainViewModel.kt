package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.DecodedTexture
import com.example.core.GtaColorBreakdown
import com.example.core.GtaHashUtils
import com.example.core.JoaatResult
import com.example.core.RpfAndArchiveParser
import com.example.core.TextureDecoder
import com.example.core.XmlTools
import com.example.core.XmlTreeNode
import com.example.core.XmlValidationResult
import com.example.data.ArchiveType
import com.example.data.BinarySource
import com.example.data.ExplorerFilter
import com.example.data.ExplorerLocation
import com.example.data.ModFileEntry
import com.example.data.ModItemKind
import com.example.data.OivInstallManager
import com.example.data.OivInstallTarget
import com.example.data.OivInstalledRecord
import com.example.data.PreferencesManager
import com.example.data.TextureItemRef
import com.example.data.WorkspaceRepository
import com.example.data.classifyFileKind
import com.example.ui.components.OivInstallerUiState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TextureChannel(val label: String) {
  RGB("RGB"),
  RED("R"),
  GREEN("G"),
  BLUE("B"),
  ALPHA("Alpha"),
}

enum class ViewerBgMode(val label: String) {
  CHECKERBOARD("Transparan"),
  DARK("Gelap"),
  LIGHT("Terang"),
}

data class SampledPixelInfo(
  val x: Int,
  val y: Int,
  val argb: Int,
  val hex: String,
  val rgbaText: String,
)

data class ExplorerUiState(
  val location: ExplorerLocation = ExplorerLocation.LocalDir("GTA V"),
  val entries: List<ModFileEntry> = emptyList(),
  val filter: ExplorerFilter = ExplorerFilter.ALL,
  val searchQuery: String = "",
  val isLoading: Boolean = true,
  val diagnosticBanner: String? = null,
  val errorMessage: String? = null,
)

data class XmlEditorUiState(
  val fileName: String = "",
  val source: BinarySource? = null,
  val textValue: TextFieldValue = TextFieldValue(""),
  val undoStack: List<TextFieldValue> = emptyList(),
  val redoStack: List<TextFieldValue> = emptyList(),
  val isDirty: Boolean = false,
  val isReadOnlyArchive: Boolean = false,
  val validationResult: XmlValidationResult? = null,
  val treeNode: XmlTreeNode? = null,
  val isTreeMode: Boolean = false,
  val showFindBar: Boolean = false,
  val findQuery: String = "",
  val replaceQuery: String = "",
  val wordWrap: Boolean = false,
  val showLineNumbers: Boolean = true,
  val isLoading: Boolean = false,
)

data class ImageViewerUiState(
  val fileName: String = "",
  val source: BinarySource? = null,
  val decoded: DecodedTexture? = null,
  val siblings: List<TextureItemRef> = emptyList(),
  val selectedChannel: TextureChannel = TextureChannel.RGB,
  val bgMode: ViewerBgMode = ViewerBgMode.CHECKERBOARD,
  val showGridOverlay: Boolean = false,
  val rotationDegrees: Float = 0f,
  val sampledPixel: SampledPixelInfo? = null,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
)

data class ToolsUiState(
  val hashInput: String = "adder",
  val hashResult: JoaatResult = GtaHashUtils.joaat("adder"),
  val colorHexInput: String = "#00E5A3",
  val colorBreakdown: GtaColorBreakdown? = GtaHashUtils.parseHexColor("#00E5A3"),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val repo = WorkspaceRepository(application)
  private val oivManager = OivInstallManager(application, repo)
  private val prefs = PreferencesManager(application)

  private val _editMode = MutableStateFlow(false)
  val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

  private val _recentPaths = MutableStateFlow<List<String>>(emptyList())
  val recentPaths: StateFlow<List<String>> = _recentPaths.asStateFlow()

  private val _explorerState = MutableStateFlow(ExplorerUiState())
  val explorerState: StateFlow<ExplorerUiState> = _explorerState.asStateFlow()

  private val _xmlState = MutableStateFlow(XmlEditorUiState())
  val xmlState: StateFlow<XmlEditorUiState> = _xmlState.asStateFlow()

  private val _imageState = MutableStateFlow(ImageViewerUiState())
  val imageState: StateFlow<ImageViewerUiState> = _imageState.asStateFlow()

  private val _toolsState = MutableStateFlow(ToolsUiState())
  val toolsState: StateFlow<ToolsUiState> = _toolsState.asStateFlow()

  private val _oivInstallerState = MutableStateFlow(OivInstallerUiState())
  val oivInstallerState: StateFlow<OivInstallerUiState> = _oivInstallerState.asStateFlow()

  private val _installedOivPackages = MutableStateFlow<List<OivInstalledRecord>>(emptyList())
  val installedOivPackages: StateFlow<List<OivInstalledRecord>> = _installedOivPackages.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  init {
    viewModelScope.launch {
      repo.ensureSampleWorkspaceInitialized()
      loadLocation(ExplorerLocation.LocalDir("GTA V"))
      refreshInstalledOivPackages()
    }
    viewModelScope.launch {
      prefs.prefsFlow.collect { p ->
        _editMode.value = p.editMode
        _recentPaths.value = p.recentLocalPaths
        _xmlState.update { it.copy(wordWrap = p.wordWrap, showLineNumbers = p.showLineNumbers) }
      }
    }
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  fun showToast(msg: String) {
    _snackbarMessage.value = msg
  }

  fun toggleEditMode() {
    val next = !_editMode.value
    _editMode.value = next
    viewModelScope.launch { prefs.setEditMode(next) }
    showToast(
      if (next) "Edit Mode AKTIF — Anda dapat mengubah & menyimpan file"
      else "Edit Mode NONAKTIF — Mode baca-saja aman"
    )
  }

  fun setExplorerFilter(filter: ExplorerFilter) {
    _explorerState.update { it.copy(filter = filter) }
  }

  fun setExplorerSearch(query: String) {
    _explorerState.update { it.copy(searchQuery = query) }
  }

  fun openLocalWorkspaceHome() {
    loadLocation(ExplorerLocation.LocalDir("GTA V"))
  }

  fun resetSampleWorkspace() {
    viewModelScope.launch {
      _explorerState.update { it.copy(isLoading = true) }
      repo.resetSampleWorkspace()
      loadLocation(ExplorerLocation.LocalDir("GTA V"))
      refreshInstalledOivPackages()
      showToast("Workspace GTA V dikembalikan ke bawaan")
    }
  }

  fun loadLocation(location: ExplorerLocation) {
    viewModelScope.launch {
      _explorerState.update {
        it.copy(
          location = location,
          isLoading = true,
          errorMessage = null,
          diagnosticBanner = null,
        )
      }
      when (location) {
        is ExplorerLocation.LocalDir -> {
          val list = repo.listLocalDirectory(location.relativePath)
          _explorerState.update {
            it.copy(
              entries = list,
              isLoading = false,
              diagnosticBanner =
                if (location.relativePath.endsWith(".ytd", true)) {
                  "Texture Dictionary (.ytd) • Ketuk tekstur untuk membuka Penampil Gambar"
                } else null,
            )
          }
        }
        is ExplorerLocation.SafTreeDir -> {
          runCatching { repo.listSafDirectory(location.treeUri, location.documentId) }
            .onSuccess { list ->
              _explorerState.update { it.copy(entries = list, isLoading = false) }
            }
            .onFailure { err ->
              _explorerState.update {
                it.copy(isLoading = false, errorMessage = err.message ?: "Gagal membaca folder perangkat")
              }
            }
        }
        is ExplorerLocation.ArchiveView -> {
          repo.listArchiveDirectory(location.archiveSource, location.archiveType, location.innerPath)
            .onSuccess { res ->
              _explorerState.update {
                it.copy(
                  entries = res.entries,
                  isLoading = false,
                  diagnosticBanner = res.diagnostic,
                )
              }
            }
            .onFailure { err ->
              _explorerState.update {
                it.copy(isLoading = false, errorMessage = err.message ?: "Gagal membaca arsip")
              }
            }
        }
      }
    }
  }

  fun navigateUp(): Boolean {
    return when (val loc = _explorerState.value.location) {
      is ExplorerLocation.LocalDir -> {
        val clean = loc.relativePath.trim('/')
        if (clean.isEmpty() || clean == "GTA V") {
          false
        } else {
          val parent = clean.substringBeforeLast('/', "")
          loadLocation(ExplorerLocation.LocalDir(parent.ifEmpty { "GTA V" }))
          true
        }
      }
      is ExplorerLocation.ArchiveView -> {
        val cleanInner = loc.innerPath.trim('/')
        if (cleanInner.isNotEmpty()) {
          val parentInner = cleanInner.substringBeforeLast('/', "")
          loadLocation(loc.copy(innerPath = parentInner))
        } else if (loc.parentLocalPath != null) {
          loadLocation(ExplorerLocation.LocalDir(loc.parentLocalPath))
        } else {
          loadLocation(ExplorerLocation.LocalDir("GTA V"))
        }
        true
      }
      is ExplorerLocation.SafTreeDir -> {
        if (loc.displayStack.size > 1) {
          val newStack = loc.displayStack.dropLast(1)
          loadLocation(
            loc.copy(
              documentId = newStack.last().first,
              displayStack = newStack,
            )
          )
        } else {
          loadLocation(ExplorerLocation.LocalDir("GTA V"))
        }
        true
      }
    }
  }

  fun onEntryClicked(
    entry: ModFileEntry,
    onOpenXml: () -> Unit,
    onOpenImage: () -> Unit,
  ) {
    val currentLoc = _explorerState.value.location
    when (entry.kind) {
      ModItemKind.FOLDER, ModItemKind.YTD_DICTIONARY -> {
        when (currentLoc) {
          is ExplorerLocation.LocalDir -> {
            val nextPath = entry.localPath ?: return
            loadLocation(ExplorerLocation.LocalDir(nextPath))
          }
          is ExplorerLocation.ArchiveView -> {
            val inner = entry.archiveInnerPath ?: return
            loadLocation(currentLoc.copy(innerPath = inner))
          }
          is ExplorerLocation.SafTreeDir -> {
            val docId = entry.safDocId ?: return
            loadLocation(
              currentLoc.copy(
                documentId = docId,
                displayStack = currentLoc.displayStack + (docId to entry.name),
              )
            )
          }
        }
      }
      ModItemKind.RPF_ARCHIVE, ModItemKind.OIV_ZIP_ARCHIVE -> {
        val archType =
          if (entry.kind == ModItemKind.RPF_ARCHIVE) ArchiveType.RPF7 else ArchiveType.ZIP_OR_OIV
        val source: BinarySource? =
          when (currentLoc) {
            is ExplorerLocation.LocalDir ->
              entry.localPath?.let { BinarySource.LocalFile(repo.getLocalFile(it).absolutePath) }
            is ExplorerLocation.SafTreeDir ->
              entry.safUri?.let { BinarySource.SafUri(it, entry.name) }
            is ExplorerLocation.ArchiveView ->
              entry.archiveInnerPath?.let {
                BinarySource.ArchiveInner(currentLoc.archiveSource, currentLoc.archiveType, it)
              }
          }
        if (source != null) {
          if (entry.name.endsWith(".oiv", ignoreCase = true)) {
            openOivInstaller(entry.name, source)
          } else {
            val parentLocal = (currentLoc as? ExplorerLocation.LocalDir)?.relativePath
            loadLocation(
              ExplorerLocation.ArchiveView(
                archiveTitle = entry.name,
                archiveSource = source,
                archiveType = archType,
                innerPath = "",
                parentLocalPath = parentLocal,
              )
            )
          }
        }
      }
      ModItemKind.XML_META -> {
        val source = resolveBinarySourceForEntry(currentLoc, entry) ?: return
        openXmlSource(entry.name, source, entry.localPath)
        onOpenXml()
      }
      ModItemKind.IMAGE_TEXTURE -> {
        val source = resolveBinarySourceForEntry(currentLoc, entry) ?: return
        openImageSource(entry.name, source)
        onOpenImage()
      }
      ModItemKind.OTHER -> {
        // Attempt text or show info
        showToast("${entry.name} (${entry.badge}) • Format biner mentah")
      }
    }
  }

  private fun resolveBinarySourceForEntry(
    loc: ExplorerLocation,
    entry: ModFileEntry,
  ): BinarySource? {
    return when (loc) {
      is ExplorerLocation.LocalDir ->
        entry.localPath?.let { BinarySource.LocalFile(repo.getLocalFile(it).absolutePath) }
      is ExplorerLocation.SafTreeDir ->
        entry.safUri?.let { BinarySource.SafUri(it, entry.name) }
      is ExplorerLocation.ArchiveView ->
        entry.archiveInnerPath?.let {
          BinarySource.ArchiveInner(loc.archiveSource, loc.archiveType, it)
        }
    }
  }

  fun openSafTree(treeUri: Uri) {
    try {
      getApplication<Application>().contentResolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
    }
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val label = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Folder Perangkat"
    loadLocation(
      ExplorerLocation.SafTreeDir(
        treeUri = treeUri,
        documentId = rootDocId,
        displayStack = listOf(rootDocId to label),
      )
    )
  }

  fun openSafSingleFile(
    uri: Uri,
    onOpenXml: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenedArchive: () -> Unit,
  ) {
    try {
      getApplication<Application>().contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
    }
    val name = repo.resolveSafDisplayName(uri)
    val kind = classifyFileKind(name, isDirectory = false)
    val src = BinarySource.SafUri(uri, name)
    when (kind) {
      ModItemKind.RPF_ARCHIVE -> {
        loadLocation(
          ExplorerLocation.ArchiveView(
            archiveTitle = name,
            archiveSource = src,
            archiveType = ArchiveType.RPF7,
            innerPath = "",
            parentLocalPath = "GTA V",
          )
        )
        onOpenedArchive()
      }
      ModItemKind.OIV_ZIP_ARCHIVE -> {
        if (name.endsWith(".oiv", ignoreCase = true)) {
          openOivInstaller(name, src)
        } else {
          loadLocation(
            ExplorerLocation.ArchiveView(
              archiveTitle = name,
              archiveSource = src,
              archiveType = ArchiveType.ZIP_OR_OIV,
              innerPath = "",
              parentLocalPath = "GTA V",
            )
          )
        }
        onOpenedArchive()
      }
      ModItemKind.IMAGE_TEXTURE -> {
        openImageSource(name, src)
        onOpenImage()
      }
      else -> {
        openXmlSource(name, src, null)
        onOpenXml()
      }
    }
  }

  fun createNewLocalXml(
    fileName: String,
    templateType: String,
    onOpenXml: () -> Unit,
  ) {
    val loc = _explorerState.value.location as? ExplorerLocation.LocalDir ?: return
    val content =
      when (templateType) {
        "handling" -> WorkspaceRepository.SAMPLE_HANDLING_META
        "vehicles" -> WorkspaceRepository.SAMPLE_VEHICLES_META
        "assembly" -> WorkspaceRepository.SAMPLE_OIV_ASSEMBLY_XML
        else ->
          """<?xml version="1.0" encoding="UTF-8"?>
<ModConfig>
  <Item name="contoh_item">
    <Value value="1.0000" />
  </Item>
</ModConfig>"""
      }
    val finalName =
      if (fileName.contains('.')) fileName.trim() else "${fileName.trim()}.xml"
    viewModelScope.launch {
      repo.saveTextToLocalWorkspace(loc.relativePath, finalName, content)
        .onSuccess { localSrc ->
          loadLocation(loc)
          openXmlSource(finalName, localSrc, "${loc.relativePath}/$finalName")
          onOpenXml()
          showToast("File $finalName dibuat")
        }
        .onFailure { showToast(it.message ?: "Gagal membuat file") }
    }
  }

  fun createNewLocalFolder(folderName: String) {
    val loc = _explorerState.value.location as? ExplorerLocation.LocalDir ?: return
    viewModelScope.launch {
      repo.createFolderInLocal(loc.relativePath, folderName)
        .onSuccess {
          loadLocation(loc)
          showToast("Folder '$folderName' dibuat")
        }
        .onFailure { showToast(it.message ?: "Gagal membuat folder") }
    }
  }

  fun deleteLocalEntry(entry: ModFileEntry) {
    val path = entry.localPath ?: return
    val loc = _explorerState.value.location as? ExplorerLocation.LocalDir ?: return
    viewModelScope.launch {
      repo.deleteLocalItem(path)
        .onSuccess {
          loadLocation(loc)
          showToast("${entry.name} dihapus")
        }
        .onFailure { showToast(it.message ?: "Gagal menghapus") }
    }
  }

  fun renameLocalEntry(
    entry: ModFileEntry,
    newName: String,
  ) {
    val path = entry.localPath ?: return
    val loc = _explorerState.value.location as? ExplorerLocation.LocalDir ?: return
    viewModelScope.launch {
      repo.renameLocalItem(path, newName)
        .onSuccess {
          loadLocation(loc)
          showToast("Diubah menjadi $newName")
        }
        .onFailure { showToast(it.message ?: "Gagal mengubah nama") }
    }
  }

  // ==================== XML EDITOR ====================

  fun openLocalRelativeXml(relativePath: String) {
    val file = repo.getLocalFile(relativePath)
    if (file.exists()) {
      openXmlSource(file.name, BinarySource.LocalFile(file.absolutePath), relativePath)
    } else {
      showToast("File tidak ditemukan: $relativePath")
    }
  }

  fun openXmlSource(
    name: String,
    source: BinarySource,
    localRelPath: String?,
  ) {
    viewModelScope.launch {
      _xmlState.update { it.copy(isLoading = true, fileName = name, source = source) }
      repo.readTextSource(source)
        .onSuccess { text ->
          val tfv = TextFieldValue(text, TextRange(0))
          val validation = XmlTools.validateXml(text)
          val tree = XmlTools.parseXmlTree(text).getOrNull()
          _xmlState.update {
            it.copy(
              fileName = name,
              source = source,
              textValue = tfv,
              undoStack = emptyList(),
              redoStack = emptyList(),
              isDirty = false,
              isReadOnlyArchive = source is BinarySource.ArchiveInner,
              validationResult = validation,
              treeNode = tree,
              isLoading = false,
            )
          }
          if (localRelPath != null) {
            prefs.addRecentPath(localRelPath)
          }
        }
        .onFailure { err ->
          _xmlState.update { it.copy(isLoading = false) }
          showToast(err.message ?: "Gagal membaca file XML")
        }
    }
  }

  fun updateXmlText(newValue: TextFieldValue) {
    val current = _xmlState.value
    if (newValue.text == current.textValue.text) {
      // Only cursor/selection changed
      _xmlState.update { it.copy(textValue = newValue) }
      return
    }
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode di bilah atas untuk mengedit teks")
      return
    }
    val nextUndo = (current.undoStack + current.textValue).takeLast(40)
    _xmlState.update {
      it.copy(
        textValue = newValue,
        undoStack = nextUndo,
        redoStack = emptyList(),
        isDirty = true,
      )
    }
  }

  fun undoXml() {
    val current = _xmlState.value
    val prev = current.undoStack.lastOrNull() ?: return
    _xmlState.update {
      it.copy(
        textValue = prev,
        undoStack = current.undoStack.dropLast(1),
        redoStack = (current.redoStack + current.textValue).takeLast(40),
        isDirty = true,
      )
    }
  }

  fun redoXml() {
    val current = _xmlState.value
    val next = current.redoStack.lastOrNull() ?: return
    _xmlState.update {
      it.copy(
        textValue = next,
        undoStack = (current.undoStack + current.textValue).takeLast(40),
        redoStack = current.redoStack.dropLast(1),
        isDirty = true,
      )
    }
  }

  fun validateCurrentXml() {
    val txt = _xmlState.value.textValue.text
    val res = XmlTools.validateXml(txt)
    val tree = XmlTools.parseXmlTree(txt).getOrNull()
    _xmlState.update { it.copy(validationResult = res, treeNode = tree) }
  }

  fun formatCurrentXml() {
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode untuk memformat XML")
      return
    }
    val current = _xmlState.value
    XmlTools.formatXml(current.textValue.text)
      .onSuccess { formatted ->
        val nextUndo = (current.undoStack + current.textValue).takeLast(40)
        val newTfv = TextFieldValue(formatted, TextRange(0))
        _xmlState.update {
          it.copy(
            textValue = newTfv,
            undoStack = nextUndo,
            redoStack = emptyList(),
            isDirty = true,
            validationResult = XmlTools.validateXml(formatted),
            treeNode = XmlTools.parseXmlTree(formatted).getOrNull(),
          )
        }
        showToast("XML dirapikan otomatis")
      }
      .onFailure { err ->
        validateCurrentXml()
        showToast("Format gagal: periksa sintaks XML (${err.message ?: "invalid"})")
      }
  }

  fun insertXmlSnippet(snippet: String) {
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode untuk menyisipkan tag")
      return
    }
    val cur = _xmlState.value.textValue
    val selStart = cur.selection.min.coerceIn(0, cur.text.length)
    val selEnd = cur.selection.max.coerceIn(selStart, cur.text.length)
    val updated = cur.text.substring(0, selStart) + snippet + cur.text.substring(selEnd)
    val newCursor = selStart + snippet.length
    updateXmlText(TextFieldValue(updated, TextRange(newCursor)))
  }

  fun toggleXmlTreeMode(treeMode: Boolean) {
    if (treeMode) {
      val tree = XmlTools.parseXmlTree(_xmlState.value.textValue.text).getOrNull()
      _xmlState.update { it.copy(isTreeMode = true, treeNode = tree) }
    } else {
      _xmlState.update { it.copy(isTreeMode = false) }
    }
  }

  fun toggleFindBar() {
    _xmlState.update { it.copy(showFindBar = !it.showFindBar) }
  }

  fun updateFindQuery(q: String) {
    _xmlState.update { it.copy(findQuery = q) }
  }

  fun updateReplaceQuery(q: String) {
    _xmlState.update { it.copy(replaceQuery = q) }
  }

  fun findNextOccurrence() {
    val state = _xmlState.value
    val q = state.findQuery
    if (q.isEmpty()) return
    val text = state.textValue.text
    val startFrom = (state.textValue.selection.end).coerceIn(0, text.length)
    var idx = text.indexOf(q, startIndex = startFrom, ignoreCase = true)
    if (idx == -1 && startFrom > 0) {
      idx = text.indexOf(q, startIndex = 0, ignoreCase = true)
    }
    if (idx >= 0) {
      _xmlState.update {
        it.copy(textValue = state.textValue.copy(selection = TextRange(idx, idx + q.length)))
      }
    } else {
      showToast("Teks '$q' tidak ditemukan")
    }
  }

  fun replaceAllOccurrences() {
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode untuk mengganti teks")
      return
    }
    val state = _xmlState.value
    val q = state.findQuery
    if (q.isEmpty()) return
    val text = state.textValue.text
    val count = Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(text).count()
    if (count == 0) {
      showToast("Tidak ada kecocokan untuk diganti")
      return
    }
    val replaced = text.replace(q, state.replaceQuery, ignoreCase = true)
    updateXmlText(TextFieldValue(replaced, TextRange(0)))
    showToast("$count kecocokan diganti")
  }

  fun toggleWordWrap() {
    val next = !_xmlState.value.wordWrap
    _xmlState.update { it.copy(wordWrap = next) }
    viewModelScope.launch { prefs.setWordWrap(next) }
  }

  fun toggleLineNumbers() {
    val next = !_xmlState.value.showLineNumbers
    _xmlState.update { it.copy(showLineNumbers = next) }
    viewModelScope.launch { prefs.setShowLineNumbers(next) }
  }

  fun saveCurrentXml() {
    val state = _xmlState.value
    val src = state.source ?: return
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode untuk menyimpan perubahan")
      return
    }
    viewModelScope.launch {
      if (src is BinarySource.ArchiveInner) {
        val exportName = state.fileName.ifBlank { "extracted_mod.xml" }
        repo.saveTextToLocalWorkspace("GTA V/mods", exportName, state.textValue.text)
          .onSuccess { newLocal ->
            _xmlState.update {
              it.copy(
                source = newLocal,
                isDirty = false,
                isReadOnlyArchive = false,
                validationResult = XmlTools.validateXml(state.textValue.text),
              )
            }
            showToast("Disimpan ke GTA V/mods/$exportName")
          }
          .onFailure { showToast(it.message ?: "Gagal menyimpan") }
      } else {
        repo.writeTextSource(src, state.textValue.text)
          .onSuccess {
            _xmlState.update {
              it.copy(
                isDirty = false,
                validationResult = XmlTools.validateXml(state.textValue.text),
              )
            }
            showToast("Perubahan ${state.fileName} berhasil disimpan")
          }
          .onFailure { showToast(it.message ?: "Gagal menyimpan file") }
      }
    }
  }

  // ==================== IMAGE & TEXTURE VIEWER ====================

  fun openLocalRelativeImage(relativePath: String) {
    val file = repo.getLocalFile(relativePath)
    if (file.exists()) {
      openImageSource(file.name, BinarySource.LocalFile(file.absolutePath))
    }
  }

  fun openImageSource(
    name: String,
    source: BinarySource,
  ) {
    viewModelScope.launch {
      val siblings =
        if (source is BinarySource.LocalFile) {
          repo.collectSiblingTexturesLocal(source.absolutePath)
        } else {
          listOf(TextureItemRef(name, source))
        }
      _imageState.update {
        it.copy(
          fileName = name,
          source = source,
          siblings = siblings,
          isLoading = true,
          errorMessage = null,
          rotationDegrees = 0f,
          sampledPixel = null,
        )
      }
      val bytesRes = repo.readBinarySource(source)
      val bytes =
        bytesRes.getOrElse { err ->
          _imageState.update {
            it.copy(isLoading = false, errorMessage = err.message ?: "Gagal membaca file")
          }
          return@launch
        }
      TextureDecoder.decode(bytes, name)
        .onSuccess { decoded ->
          val cx = (decoded.width / 2).coerceAtLeast(0)
          val cy = (decoded.height / 2).coerceAtLeast(0)
          val centerSample =
            if (decoded.width > 0 && decoded.height > 0) {
              buildPixelSample(decoded.bitmap.getPixel(cx, cy), cx, cy)
            } else null
          _imageState.update {
            it.copy(
              decoded = decoded,
              sampledPixel = centerSample,
              isLoading = false,
              errorMessage = null,
            )
          }
        }
        .onFailure { err ->
          _imageState.update {
            it.copy(
              decoded = null,
              isLoading = false,
              errorMessage = err.message ?: "Gagal mendekode tekstur",
            )
          }
        }
    }
  }

  fun selectImageChannel(channel: TextureChannel) {
    _imageState.update { it.copy(selectedChannel = channel) }
  }

  fun cycleViewerBackground() {
    val next =
      when (_imageState.value.bgMode) {
        ViewerBgMode.CHECKERBOARD -> ViewerBgMode.DARK
        ViewerBgMode.DARK -> ViewerBgMode.LIGHT
        ViewerBgMode.LIGHT -> ViewerBgMode.CHECKERBOARD
      }
    _imageState.update { it.copy(bgMode = next) }
  }

  fun toggleGridOverlay() {
    _imageState.update { it.copy(showGridOverlay = !it.showGridOverlay) }
  }

  fun rotateImage90() {
    _imageState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f) }
  }

  fun samplePixelAtNormalized(
    normX: Float,
    normY: Float,
  ) {
    val decoded = _imageState.value.decoded ?: return
    val px = (normX * decoded.width).toInt().coerceIn(0, decoded.width - 1)
    val py = (normY * decoded.height).toInt().coerceIn(0, decoded.height - 1)
    val argb = decoded.bitmap.getPixel(px, py)
    _imageState.update { it.copy(sampledPixel = buildPixelSample(argb, px, py)) }
  }

  fun exportCurrentImageAsPngToUri(uri: Uri) {
    val decoded = _imageState.value.decoded ?: return
    viewModelScope.launch {
      val png = TextureDecoder.bitmapToPngBytes(decoded.bitmap)
      repo.exportBytesToSafUri(uri, png)
        .onSuccess { showToast("Tekstur diekspor sebagai PNG") }
        .onFailure { showToast(it.message ?: "Gagal mengekspor gambar") }
    }
  }

  private fun buildPixelSample(
    argb: Int,
    x: Int,
    y: Int,
  ): SampledPixelInfo {
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    val hex = String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    return SampledPixelInfo(
      x = x,
      y = y,
      argb = argb,
      hex = hex,
      rgbaText = "R:$r  G:$g  B:$b  A:$a",
    )
  }

  // ==================== TOOLS ====================

  fun updateHashInput(input: String) {
    _toolsState.update {
      it.copy(
        hashInput = input,
        hashResult = GtaHashUtils.joaat(input),
      )
    }
  }

  fun updateColorHexInput(input: String) {
    _toolsState.update {
      it.copy(
        colorHexInput = input,
        colorBreakdown = GtaHashUtils.parseHexColor(input),
      )
    }
  }

  fun getLocalDirectorySizeInfo(): Pair<Int, Long> {
    var count = 0
    var bytes = 0L
    fun walk(dir: File) {
      dir.listFiles()?.forEach { f ->
        if (!f.name.startsWith(".")) {
          if (f.isDirectory) walk(f)
          else {
            count++
            bytes += f.length()
          }
        }
      }
    }
    walk(repo.workspaceRoot)
    return count to bytes
  }

  // ==================== OIV PACKAGE INSTALLER ====================

  fun openLocalRelativeOiv(
    name: String,
    relativePath: String,
  ) {
    val file = repo.getLocalFile(relativePath)
    if (file.exists()) {
      openOivInstaller(name, BinarySource.LocalFile(file.absolutePath))
    } else {
      showToast("Paket .oiv tidak ditemukan")
    }
  }

  fun openOivFromSafUri(uri: Uri) {
    try {
      getApplication<Application>().contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
    }
    val name = repo.resolveSafDisplayName(uri)
    openOivInstaller(name, BinarySource.SafUri(uri, name))
  }

  fun openOivInstaller(
    name: String,
    source: BinarySource,
  ) {
    _oivInstallerState.value =
      OivInstallerUiState(
        isVisible = true,
        fileName = name,
        source = source,
        isParsing = true,
      )
    viewModelScope.launch {
      oivManager.inspectOiv(source)
        .onSuccess { manifest ->
          _oivInstallerState.update {
            it.copy(isParsing = false, manifest = manifest, parseError = null)
          }
        }
        .onFailure { err ->
          _oivInstallerState.update {
            it.copy(isParsing = false, parseError = err.message ?: "Gagal membaca paket .oiv")
          }
        }
    }
  }

  fun dismissOivInstaller() {
    _oivInstallerState.value = OivInstallerUiState()
  }

  fun enableEditModeExplicitly() {
    if (!_editMode.value) {
      _editMode.value = true
      viewModelScope.launch { prefs.setEditMode(true) }
      showToast("Edit Mode AKTIF")
    }
  }

  fun browseCurrentOivInExplorer() {
    val st = _oivInstallerState.value
    val src = st.source ?: return
    val parentLocal = (_explorerState.value.location as? ExplorerLocation.LocalDir)?.relativePath
    _oivInstallerState.value = OivInstallerUiState()
    loadLocation(
      ExplorerLocation.ArchiveView(
        archiveTitle = st.fileName,
        archiveSource = src,
        archiveType = ArchiveType.ZIP_OR_OIV,
        innerPath = "",
        parentLocalPath = parentLocal,
      )
    )
  }

  fun viewCurrentOivAssemblyXml(onOpenXml: () -> Unit) {
    val st = _oivInstallerState.value
    val src = st.source ?: return
    _oivInstallerState.value = OivInstallerUiState()
    openXmlSource(
      name = "assembly.xml (${st.fileName})",
      source = BinarySource.ArchiveInner(src, ArchiveType.ZIP_OR_OIV, "assembly.xml"),
      localRelPath = null,
    )
    onOpenXml()
  }

  fun installCurrentOiv(
    target: OivInstallTarget,
    createBackup: Boolean,
  ) {
    val st = _oivInstallerState.value
    val src = st.source ?: return
    val manifest = st.manifest ?: return
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode untuk menginstal paket .oiv")
      return
    }
    _oivInstallerState.update {
      it.copy(
        isInstalling = true,
        progress = 0.05f,
        installLogs = emptyList(),
        isFinished = false,
        installedTarget = target,
      )
    }
    viewModelScope.launch {
      oivManager.installOiv(
        source = src,
        manifest = manifest,
        target = target,
        createBackup = createBackup,
        onProgress = { logLine, p ->
          _oivInstallerState.update { cur ->
            cur.copy(progress = p, installLogs = cur.installLogs + logLine)
          }
        },
      ).onSuccess { result ->
        _oivInstallerState.update {
          it.copy(isInstalling = false, progress = 1f, isFinished = true)
        }
        refreshInstalledOivPackages()
        val currentLoc = _explorerState.value.location
        if (currentLoc is ExplorerLocation.LocalDir) {
          loadLocation(currentLoc)
        }
        showToast("${manifest.metadata.name} berhasil dipasang (${result.filesWritten} file)")
      }.onFailure { err ->
        _oivInstallerState.update { cur ->
          cur.copy(
            isInstalling = false,
            installLogs = cur.installLogs + "[ERROR] ${err.message ?: "Instalasi gagal"}",
          )
        }
      }
    }
  }

  fun openInstalledOivLocation() {
    val target = _oivInstallerState.value.installedTarget
    _oivInstallerState.value = OivInstallerUiState()
    when (target) {
      is OivInstallTarget.ModsFolder -> loadLocation(ExplorerLocation.LocalDir("GTA V/mods"))
      is OivInstallTarget.MainWorkspace -> loadLocation(ExplorerLocation.LocalDir("GTA V/update"))
      is OivInstallTarget.SafFolder -> openSafTree(target.treeUri)
      null -> Unit
    }
  }

  fun refreshInstalledOivPackages() {
    viewModelScope.launch {
      _installedOivPackages.value = oivManager.getInstalledPackages()
    }
  }

  fun uninstallOivPackage(installId: String) {
    if (!_editMode.value) {
      showToast("Aktifkan Edit Mode untuk mencopot paket .oiv")
      return
    }
    viewModelScope.launch {
      oivManager.uninstallPackage(installId)
        .onSuccess { msg ->
          refreshInstalledOivPackages()
          val currentLoc = _explorerState.value.location
          if (currentLoc is ExplorerLocation.LocalDir) {
            loadLocation(currentLoc)
          }
          showToast(msg)
        }
        .onFailure { err -> showToast(err.message ?: "Gagal mencopot paket") }
    }
  }
}
