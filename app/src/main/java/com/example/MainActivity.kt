package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ExplorerLocation
import com.example.ui.MainViewModel
import com.example.ui.components.OivInstallerDialog
import com.example.ui.components.OpenVHeaderBar
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.RpfEditorScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.XmlEditorScreen
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.OpenVTheme

const val ROUTE_EXPLORER = "explorer"
const val ROUTE_RPF_EDITOR = "rpf_editor"
const val ROUTE_XML_EDITOR = "xml_editor"
const val ROUTE_IMAGE_VIEWER = "image_viewer"
const val ROUTE_TOOLS = "tools"

private data class NavDestinationItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val navItems =
  listOf(
    NavDestinationItem(
      route = ROUTE_EXPLORER,
      label = "Explorer",
      selectedIcon = Icons.Rounded.Folder,
      unselectedIcon = Icons.Outlined.Folder,
      testTag = "nav_explorer",
    ),
    NavDestinationItem(
      route = ROUTE_RPF_EDITOR,
      label = "RPF",
      selectedIcon = Icons.Rounded.Inventory2,
      unselectedIcon = Icons.Outlined.Inventory2,
      testTag = "nav_rpf_editor",
    ),
    NavDestinationItem(
      route = ROUTE_XML_EDITOR,
      label = "XML",
      selectedIcon = Icons.Rounded.Code,
      unselectedIcon = Icons.Outlined.Code,
      testTag = "nav_xml_editor",
    ),
    NavDestinationItem(
      route = ROUTE_IMAGE_VIEWER,
      label = "Gambar",
      selectedIcon = Icons.Rounded.Image,
      unselectedIcon = Icons.Outlined.Image,
      testTag = "nav_image_viewer",
    ),
    NavDestinationItem(
      route = ROUTE_TOOLS,
      label = "Alat",
      selectedIcon = Icons.Rounded.Build,
      unselectedIcon = Icons.Outlined.Build,
      testTag = "nav_tools",
    ),
  )

class MainActivity : ComponentActivity() {
  private var pendingExternalUri by mutableStateOf<Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    pendingExternalUri = extractIncomingUri(intent)

    setContent {
      OpenVTheme(darkTheme = true) {
        OpenVApp(
          pendingExternalUri = pendingExternalUri,
          onConsumedExternalUri = { pendingExternalUri = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractIncomingUri(intent)?.let { pendingExternalUri = it }
  }

  private fun extractIncomingUri(intent: Intent?): Uri? {
    if (intent == null) return null
    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data
      Intent.ACTION_SEND -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
      }
      else -> null
    }
  }
}

@Composable
fun OpenVApp(
  pendingExternalUri: Uri? = null,
  onConsumedExternalUri: () -> Unit = {},
  viewModel: MainViewModel = viewModel(),
) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: ROUTE_EXPLORER

  val editMode by viewModel.editMode.collectAsStateWithLifecycle()
  val recentPaths by viewModel.recentPaths.collectAsStateWithLifecycle()
  val explorerState by viewModel.explorerState.collectAsStateWithLifecycle()
  val rpfState by viewModel.rpfState.collectAsStateWithLifecycle()
  val xmlState by viewModel.xmlState.collectAsStateWithLifecycle()
  val imageState by viewModel.imageState.collectAsStateWithLifecycle()
  val toolsState by viewModel.toolsState.collectAsStateWithLifecycle()
  val oivInstallerState by viewModel.oivInstallerState.collectAsStateWithLifecycle()
  val installedOivPackages by viewModel.installedOivPackages.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarMessage) {
    val msg = snackbarMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearSnackbar()
  }

  fun navigateTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(pendingExternalUri) {
    val uri = pendingExternalUri ?: return@LaunchedEffect
    viewModel.openSafSingleFile(
      uri = uri,
      onOpenXml = { navigateTab(ROUTE_XML_EDITOR) },
      onOpenImage = { navigateTab(ROUTE_IMAGE_VIEWER) },
      onOpenedArchive = { navigateTab(ROUTE_EXPLORER) },
    )
    onConsumedExternalUri()
  }

  val headerSubtitle =
    when (currentRoute) {
      ROUTE_EXPLORER ->
        when (val loc = explorerState.location) {
          is ExplorerLocation.LocalDir -> loc.relativePath
          is ExplorerLocation.ArchiveView -> "${loc.archiveTitle}/${loc.innerPath}".trimEnd('/')
          is ExplorerLocation.SafTreeDir -> loc.displayStack.joinToString("/") { it.second }
        }
      ROUTE_RPF_EDITOR ->
        rpfState.archiveName.ifEmpty { "Editor Arsip RPF7 GTA V" }
      ROUTE_XML_EDITOR ->
        xmlState.fileName.ifEmpty { "Editor XML & Meta GTA V" }
      ROUTE_IMAGE_VIEWER ->
        imageState.fileName.ifEmpty { "Penampil Tekstur & Gambar" }
      ROUTE_TOOLS -> "Utilitas JOAAT Hash & Warna"
      else -> "GTA V Modding Suite"
    }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.imePadding()) },
      topBar = {
        OpenVHeaderBar(
          title = "OpenV Studio",
          subtitle = headerSubtitle,
          editMode = editMode,
          onToggleEditMode = viewModel::toggleEditMode,
        )
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = {
                  Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = EmeraldPrimary,
                    selectedTextColor = EmeraldPrimary,
                    indicatorColor = EmeraldContainer,
                  ),
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding(),
      ) {
        if (useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier =
              Modifier.fillMaxHeight()
                .windowInsetsPadding(
                  WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start)
                ),
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTab(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = {
                  Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = EmeraldOnContainer,
                    selectedTextColor = EmeraldPrimary,
                    indicatorColor = EmeraldContainer,
                  ),
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(
            navController = navController,
            startDestination = ROUTE_EXPLORER,
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = {
              scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin.Center,
              ) + fadeOut(animationSpec = tween(180))
            },
            modifier = Modifier.fillMaxSize(),
          ) {
            composable(ROUTE_EXPLORER) {
              ExplorerScreen(
                state = explorerState,
                editMode = editMode,
                onNavigateUp = viewModel::navigateUp,
                onOpenWorkspaceHome = viewModel::openLocalWorkspaceHome,
                onOpenLocation = viewModel::loadLocation,
                onEntryClicked = { entry ->
                  viewModel.onEntryClicked(
                    entry = entry,
                    onOpenXml = { navigateTab(ROUTE_XML_EDITOR) },
                    onOpenImage = { navigateTab(ROUTE_IMAGE_VIEWER) },
                  )
                },
                onFilterSelected = viewModel::setExplorerFilter,
                onSearchChanged = viewModel::setExplorerSearch,
                onSafTreePicked = viewModel::openSafTree,
                onSafFilePicked = { uri ->
                  viewModel.openSafSingleFile(
                    uri = uri,
                    onOpenXml = { navigateTab(ROUTE_XML_EDITOR) },
                    onOpenImage = { navigateTab(ROUTE_IMAGE_VIEWER) },
                    onOpenedArchive = { /* stay on explorer or show OIV dialog */ },
                  )
                },
                onInstallOivPicked = viewModel::openOivFromSafUri,
                onQuickOpenXml = { relPath ->
                  viewModel.openLocalRelativeXml(relPath)
                  navigateTab(ROUTE_XML_EDITOR)
                },
                onQuickOpenImage = { relPath ->
                  viewModel.openLocalRelativeImage(relPath)
                  navigateTab(ROUTE_IMAGE_VIEWER)
                },
                onQuickOpenOiv = { name, relPath ->
                  viewModel.openLocalRelativeOiv(name, relPath)
                },
                onQuickOpenRpfEditor = {
                  viewModel.openLocalSampleRpfInEditor()
                  navigateTab(ROUTE_RPF_EDITOR)
                },
                onOpenRpfInEditor = { title, source ->
                  viewModel.openRpfInEditor(title, source)
                  navigateTab(ROUTE_RPF_EDITOR)
                },
                onCreateNewXml = { name, tpl ->
                  viewModel.createNewLocalXml(name, tpl) { navigateTab(ROUTE_XML_EDITOR) }
                },
                onCreateNewFolder = viewModel::createNewLocalFolder,
                onRenameEntry = viewModel::renameLocalEntry,
                onDeleteEntry = viewModel::deleteLocalEntry,
              )
            }

            composable(ROUTE_RPF_EDITOR) {
              RpfEditorScreen(
                state = rpfState,
                editMode = editMode,
                onOpenSampleRpf = viewModel::openLocalSampleRpfInEditor,
                onOpenRpfSafUri = viewModel::openRpfFromSafUri,
                onCreateNewRpf = viewModel::createNewRpfArchive,
                onNavigateFolder = viewModel::navigateRpfFolder,
                onNavigateUp = viewModel::navigateRpfUp,
                onSearchChanged = viewModel::setRpfSearchQuery,
                onSelectEntryForHex = viewModel::selectRpfEntryForHex,
                onOpenEntryInXml = { entryPath ->
                  viewModel.openRpfEntryInXmlEditor(entryPath) { navigateTab(ROUTE_XML_EDITOR) }
                },
                onOpenEntryInImage = { entryPath ->
                  viewModel.openRpfEntryInImageViewer(entryPath) { navigateTab(ROUTE_IMAGE_VIEWER) }
                },
                onCreateSubfolder = viewModel::createRpfSubfolder,
                onCreateXmlFile = viewModel::createRpfXmlFile,
                onImportFileFromSaf = viewModel::importFileIntoRpfFromSaf,
                onRenameEntry = viewModel::renameRpfEntry,
                onDeleteEntry = viewModel::deleteRpfEntry,
                onExtractEntryToMods = viewModel::extractRpfEntryToMods,
                onExtractEntryToSaf = viewModel::extractRpfEntryToSafUri,
                onSaveRpf = viewModel::saveRpfArchive,
                onExportRpfToSaf = viewModel::exportRpfArchiveToSafUri,
              )
            }

            composable(ROUTE_XML_EDITOR) {
              XmlEditorScreen(
                state = xmlState,
                editMode = editMode,
                recentPaths = recentPaths,
                onTextChanged = viewModel::updateXmlText,
                onUndo = viewModel::undoXml,
                onRedo = viewModel::redoXml,
                onValidate = viewModel::validateCurrentXml,
                onFormat = viewModel::formatCurrentXml,
                onSave = viewModel::saveCurrentXml,
                onInsertSnippet = viewModel::insertXmlSnippet,
                onToggleTreeMode = viewModel::toggleXmlTreeMode,
                onToggleFindBar = viewModel::toggleFindBar,
                onFindQueryChanged = viewModel::updateFindQuery,
                onReplaceQueryChanged = viewModel::updateReplaceQuery,
                onFindNext = viewModel::findNextOccurrence,
                onReplaceAll = viewModel::replaceAllOccurrences,
                onToggleWordWrap = viewModel::toggleWordWrap,
                onToggleLineNumbers = viewModel::toggleLineNumbers,
                onOpenSampleRelative = viewModel::openLocalRelativeXml,
                onOpenSafFile = { uri ->
                  viewModel.openSafSingleFile(
                    uri = uri,
                    onOpenXml = {},
                    onOpenImage = { navigateTab(ROUTE_IMAGE_VIEWER) },
                    onOpenedArchive = { navigateTab(ROUTE_EXPLORER) },
                  )
                },
              )
            }

            composable(ROUTE_IMAGE_VIEWER) {
              ImageViewerScreen(
                state = imageState,
                onSelectChannel = viewModel::selectImageChannel,
                onCycleBackground = viewModel::cycleViewerBackground,
                onToggleGrid = viewModel::toggleGridOverlay,
                onRotate90 = viewModel::rotateImage90,
                onSamplePixelNormalized = viewModel::samplePixelAtNormalized,
                onSelectSibling = viewModel::openImageSource,
                onOpenSampleRelative = viewModel::openLocalRelativeImage,
                onOpenSafFile = { uri ->
                  viewModel.openSafSingleFile(
                    uri = uri,
                    onOpenXml = { navigateTab(ROUTE_XML_EDITOR) },
                    onOpenImage = {},
                    onOpenedArchive = { navigateTab(ROUTE_EXPLORER) },
                  )
                },
                onExportPngToUri = viewModel::exportCurrentImageAsPngToUri,
              )
            }

            composable(ROUTE_TOOLS) {
              val stats = remember(explorerState.entries) { viewModel.getLocalDirectorySizeInfo() }
              ToolsScreen(
                state = toolsState,
                installedOivPackages = installedOivPackages,
                editMode = editMode,
                workspaceStats = stats,
                onHashInputChanged = viewModel::updateHashInput,
                onColorHexChanged = viewModel::updateColorHexInput,
                onInstallOivUri = viewModel::openOivFromSafUri,
                onUninstallOiv = viewModel::uninstallOivPackage,
                onResetWorkspace = {
                  viewModel.resetSampleWorkspace()
                  navigateTab(ROUTE_EXPLORER)
                },
                onShowToast = viewModel::showToast,
              )
            }
          }
        }
      }
    }

    OivInstallerDialog(
      state = oivInstallerState,
      editMode = editMode,
      onEnableEditMode = viewModel::enableEditModeExplicitly,
      onDismiss = viewModel::dismissOivInstaller,
      onBrowseArchiveContents = viewModel::browseCurrentOivInExplorer,
      onViewAssemblyXml = {
        viewModel.viewCurrentOivAssemblyXml { navigateTab(ROUTE_XML_EDITOR) }
      },
      onInstall = viewModel::installCurrentOiv,
      onOpenInstalledLocation = {
        viewModel.openInstalledOivLocation()
        navigateTab(ROUTE_EXPLORER)
      },
    )
  }
}
