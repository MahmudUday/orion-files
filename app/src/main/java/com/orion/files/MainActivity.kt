package com.orion.files

import android.os.Bundle
import android.app.Activity
import android.media.AudioManager
import android.os.StatFs
import android.os.Environment
import android.provider.Settings
import android.os.Build
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.core.content.FileProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private val Black = Color(0xFF030407)
private val SurfaceGlass = Color(0xFF111827)
private val Blue = Color(0xFF4D8DFF)
private val Violet = Color(0xFF9B6BFF)
private val TextMain = Color(0xFFF4F5FF)
private val TextMuted = Color(0xFF9BA3BA)
private val BorderGlow = Color(0x554D8DFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(3, 4, 7)
        window.navigationBarColor = android.graphics.Color.rgb(3, 4, 7)
        setContent { OrionTheme { OrionRoot() } }
    }
}

@Composable
private fun OrionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            secondary = Violet,
            background = Black,
            surface = Color(0xFF111522),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = TextMain,
            onSurface = TextMain
        ),
        content = content
    )
}

private data class Feature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color
)

private val features = listOf(
    Feature("All Files", "Browse folders and files", Icons.Default.Folder, Blue),
    Feature("Photos & Videos", "Your media library", Icons.Default.Image, Violet),
    Feature("Downloads", "Downloaded items", Icons.Default.Download, Blue),
    Feature("Large Files", "Find space hogs", Icons.Default.Storage, Violet),
    Feature("Duplicates", "Find exact copies", Icons.Default.ContentCopy, Blue),
    Feature("ZIP Tools", "Create and extract", Icons.Default.FolderZip, Violet),
    Feature("PDF Tools", "Manage PDF documents", Icons.Default.PictureAsPdf, Blue),
    Feature("Secure Vault", "Private encrypted space", Icons.Default.Lock, Violet),
    Feature("QR Tools", "Scan and create QR", Icons.Default.QrCode2, Blue),
    Feature("Favorites", "Your starred files", Icons.Default.Star, Violet),
    Feature("Recent", "Recently opened files", Icons.Default.History, Blue),
    Feature("Storage", "Analyze device storage", Icons.Default.DonutLarge, Violet)
)

@Composable
private fun OrionRoot() {
    var tab by remember { mutableIntStateOf(0) }
    var selectedFeature by remember { mutableStateOf<Feature?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var hasAllFilesAccess by remember { mutableStateOf(Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) }
    var openedMedia by remember { mutableStateOf<File?>(null) }
    val allFilesSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasAllFilesAccess = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    }
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            selectedFolderUri = uri
        }
    }

    Box(Modifier.fillMaxSize().background(Black)) {
    Scaffold(
        containerColor = Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF090C14),
                contentColor = TextMain,
                tonalElevation = 0.dp
            ) {
                val items = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("Files", Icons.Default.Folder, 1),
                    Triple("Tools", Icons.Default.AutoAwesome, 2),
                    Triple("Settings", Icons.Default.Settings, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; selectedFeature = null },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Blue,
                            selectedTextColor = Blue,
                            indicatorColor = Color(0x332F70E8),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Black)
        ) {
            when {
                selectedFeature != null -> FeatureDetail(selectedFeature!!)
                tab == 0 -> HomeScreen(onSearch = { searchOpen = !searchOpen }, searchOpen = searchOpen, query = query, onQuery = { query = it }, onFeature = { feature -> if (feature.title == "All Files") tab = 1 else selectedFeature = feature })
                tab == 1 -> FileBrowserScreen(hasAllFilesAccess, selectedFolderUri, onRequestAllFiles = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                        runCatching { allFilesSettings.launch(intent) }.onFailure { allFilesSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }
                }, onPickFolder = { folderPicker.launch(null) }, onOpenMedia = { openedMedia = it })
                tab == 2 -> FeatureList("Smart Tools", "Useful tools in one place", features.drop(4), onFeature = { selectedFeature = it })
                else -> SettingsScreen()
            }
        }
    }
    openedMedia?.let { file -> BuiltInMediaViewer(file, onDismiss = { openedMedia = null }) }
    }
}

@Composable
private fun HomeScreen(
    onSearch: () -> Unit,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onFeature: (Feature) -> Unit
) {
    val visibleFeatures = remember(query) { features.filter { it.title.contains(query, true) || query.isBlank() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("ORION", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("FILES  •  ALL YOUR FILES. ONE PLACE.", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMain) }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blue, Violet))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White)
                }
            }
        }
        if (searchOpen) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search files and tools…", color = TextMuted) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue, unfocusedBorderColor = BorderGlow,
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain))
        }
        item(span = { GridItemSpan(maxLineSpan) }) { StorageCard() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp, bottom = 1.dp)) {
                Text("Quick Access", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("12 tools", color = Violet, fontSize = 11.sp)
            }
        }
        items(visibleFeatures, key = { it.title }) { feature -> FeatureCard(feature, onClick = { onFeature(feature) }) }
    }
}

@Composable
private fun StorageCard() {
    var refresh by remember { mutableIntStateOf(0) }
    var total by remember { mutableLongStateOf(0L) }
    var free by remember { mutableLongStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
                stat.totalBytes to stat.availableBytes
            }.getOrNull()
        }
        if (result != null) { total = result.first; free = result.second; loaded = true }
    }
    val context = LocalContext.current
    val used = (total - free).coerceAtLeast(0L)
    val ratio = if (total > 0L) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceGlass), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DonutLarge, null, tint = Blue)
                Spacer(Modifier.width(8.dp))
                Text("Device Storage", color = TextMain, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { refresh++ }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Refresh, "Refresh storage", tint = Violet) }
            }
            if (!loaded) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Blue, trackColor = Color(0xFF252B3B))
                Text("Reading device storage…", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("${android.text.format.Formatter.formatShortFileSize(context, used)} used", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color(0xFF252B3B))) {
                    Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Blue, Violet))))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("USED", color = TextMuted, fontSize = 9.sp); Text(android.text.format.Formatter.formatShortFileSize(context, used), color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Column(horizontalAlignment = Alignment.End) { Text("FREE", color = TextMuted, fontSize = 9.sp); Text(android.text.format.Formatter.formatShortFileSize(context, free), color = Color(0xFF36D6A0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Column(horizontalAlignment = Alignment.End) { Text("TOTAL", color = TextMuted, fontSize = 9.sp); Text(android.text.format.Formatter.formatShortFileSize(context, total), color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(112.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(feature.tint.copy(alpha = 0.15f))
                    .border(1.dp, feature.tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(feature.icon, contentDescription = null, tint = feature.tint) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(feature.subtitle, color = TextMuted, fontSize = 9.sp, lineHeight = 12.sp)
            }
        }
    }
}


@Composable
private fun FileBrowserScreen(
    hasAllFilesAccess: Boolean,
    rootUri: Uri?,
    onRequestAllFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenMedia: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentFolder by remember(hasAllFilesAccess, rootUri) {
        mutableStateOf<File?>(if (hasAllFilesAccess) Environment.getExternalStorageDirectory() else null)
    }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var clipboardFile by remember { mutableStateOf<File?>(null) }
    var cutMode by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(currentFolder, hasAllFilesAccess, refreshKey, query, sortByName) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            runCatching {
                currentFolder?.listFiles()?.asSequence()
                    ?.filter { it.exists() && it.name.contains(query, ignoreCase = true) }
                    ?.sortedWith(
                        if (sortByName) compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
                        else compareByDescending<File> { it.isDirectory }.thenByDescending { it.lastModified() }
                    )?.toList() ?: emptyList()
            }.getOrDefault(emptyList())
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My Files", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(currentFolder?.absolutePath ?: "Internal storage", color = TextMuted, fontSize = 10.sp, maxLines = 1)
            }
            TextButton(onClick = onPickFolder) { Text("Folder", color = Blue) }
            TextButton(onClick = { showCreateFolder = true }, enabled = hasAllFilesAccess) { Text("+ Folder", color = Violet) }
        }
        if (hasAllFilesAccess) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                placeholder = { Text("Search in this folder…", color = TextMuted) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue, unfocusedBorderColor = BorderGlow, focusedTextColor = TextMain, unfocusedTextColor = TextMain)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val parent = currentFolder?.parentFile
                    currentFolder = if (parent != null && parent.canRead()) parent else Environment.getExternalStorageDirectory()
                }) { Text("↑ Up", color = Blue) }
                TextButton(onClick = { sortByName = !sortByName }) { Text(if (sortByName) "Sort: Name" else "Sort: Recent", color = Violet) }
                Spacer(Modifier.weight(1f))
                if (clipboardFile != null) {
                    TextButton(onClick = {
                        val source = clipboardFile
                        val target = source?.let { File(currentFolder, it.name) }
                        if (source != null && target != null) {
                            loading = true
                            scope.launch(Dispatchers.IO) {
                                runCatching { source.copyRecursively(target, overwrite = true); if (cutMode) source.deleteRecursively() }
                                withContext(Dispatchers.Main) { clipboardFile = null; cutMode = false; refreshKey++ }
                            }
                        }
                    }) { Text(if (cutMode) "Move here" else "Paste", color = Blue) }
                }
            }
        }
        if (!hasAllFilesAccess) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceGlass), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Blue, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Allow full internal storage access", color = TextMain, fontWeight = FontWeight.Bold)
                    Text("Enable All files access in Android Settings to browse internal storage from its root.", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestAllFiles) { Text("Grant Full Storage Access") }
                    TextButton(onClick = onPickFolder) { Text("Or choose one folder", color = Violet) }
                }
            }
        } else if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue)
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No files found in this folder", color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp)
            ) {
                items(entries, key = { it.absolutePath }) { file ->
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceGlass), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().clickable {
                            if (file.isDirectory) currentFolder = file
                            else if (file.extension.lowercase() in listOf("jpg","jpeg","png","webp","gif","bmp","heic","avif","mp4","mkv","webm","3gp","mov")) onOpenMedia(file)
                            else runCatching {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }.onFailure { android.widget.Toast.makeText(context, "No compatible app found", android.widget.Toast.LENGTH_SHORT).show() }
                        }.padding(start = 13.dp, end = 5.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (file.isDirectory) Blue else Violet)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(if (file.isDirectory) "Folder" else android.text.format.Formatter.formatShortFileSize(context, file.length()), color = TextMuted, fontSize = 10.sp)
                            }
                            var menuOpen by remember(file.absolutePath) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { selectedFile = file; menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "File actions", tint = TextMuted) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; selectedFile = file; renameText = file.name; showRename = true })
                                    DropdownMenuItem(text = { Text("Copy") }, onClick = { menuOpen = false; clipboardFile = file; cutMode = false })
                                    DropdownMenuItem(text = { Text("Cut / Move") }, onClick = { menuOpen = false; clipboardFile = file; cutMode = true })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; selectedFile = file; showDelete = true })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRename && selectedFile != null) {
        AlertDialog(onDismissRequest = { showRename = false }, title = { Text("Rename file") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val file = selectedFile
                if (file != null && renameText.isNotBlank()) {
                    val target = File(file.parentFile, renameText.trim())
                    if (target.exists()) android.widget.Toast.makeText(context, "A file with this name already exists", android.widget.Toast.LENGTH_SHORT).show()
                    else if (file.renameTo(target)) refreshKey++
                }
                showRename = false
            }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } })
    }
    if (showDelete && selectedFile != null) {
        AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Delete permanently?") },
            text = { Text(selectedFile?.name ?: "") },
            confirmButton = { TextButton(onClick = {
                selectedFile?.let { file -> if (file.isDirectory) file.deleteRecursively() else file.delete() }
                refreshKey++; showDelete = false
            }) { Text("Delete", color = Color(0xFFFF5B70)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } })
    }
    if (showCreateFolder) {
        AlertDialog(onDismissRequest = { showCreateFolder = false }, title = { Text("New folder") },
            text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, singleLine = true, placeholder = { Text("Folder name") }) },
            confirmButton = { TextButton(onClick = {
                if (newFolderName.isNotBlank()) File(currentFolder, newFolderName.trim()).mkdirs()
                newFolderName = ""; refreshKey++; showCreateFolder = false
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("Cancel") } })
    }
}

@Composable
private fun FeatureList(title: String, subtitle: String, list: List<Feature>, onFeature: (Feature) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().widthIn(max = 500.dp)) {
            Text(title, color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
            items(list, key = { it.title }) { feature ->
                Box(Modifier.fillMaxWidth().widthIn(max = 500.dp), contentAlignment = Alignment.Center) { FeatureCard(feature) { onFeature(feature) } }
            }
        }
    }
}
@Composable
private fun FeatureDetail(feature: Feature) {
    val context = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var sourcePath by remember { mutableStateOf("") }
    var destinationPath by remember { mutableStateOf("") }
    val storage = remember {
        runCatching {
            val stat = android.os.StatFs(root.absolutePath)
            Triple(stat.totalBytes, stat.totalBytes - stat.availableBytes, stat.availableBytes)
        }.getOrNull()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().widthIn(max = 500.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(feature.title, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(feature.subtitle, color = TextMuted, fontSize = 11.sp)
            }
            Icon(feature.icon, contentDescription = null, tint = feature.tint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        when (feature.title) {
            "Storage" -> {
                if (storage == null) Text("Storage information unavailable.", color = TextMuted)
                else {
                    StorageMetric("Total", android.text.format.Formatter.formatFileSize(context, storage.first), Blue)
                    StorageMetric("Used", android.text.format.Formatter.formatFileSize(context, storage.second), Violet)
                    StorageMetric("Free", android.text.format.Formatter.formatFileSize(context, storage.third), Color(0xFF36D6A0))
                    LinearProgressIndicator(progress = { storage.second.toFloat() / storage.first.coerceAtLeast(1L) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Blue, trackColor = Color(0xFF273044))
                }
            }
            "ZIP Tools" -> {
                Text("Enter a file/folder path and output path. Example: /storage/emulated/0/Download", color = TextMuted, fontSize = 11.sp)
                OutlinedTextField(value = sourcePath, onValueChange = { sourcePath = it }, label = { Text("Source path / ZIP path") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = destinationPath, onValueChange = { destinationPath = it }, label = { Text("Output path / Extract folder") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && sourcePath.isNotBlank() && destinationPath.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        message = withContext(Dispatchers.IO) {
                            runCatching {
                                val source = File(sourcePath.trim())
                                val dest = File(destinationPath.trim())
                                require(source.exists()) { "Source path does not exist" }
                                if (source.extension.equals("zip", true) && source.isFile) {
                                    dest.mkdirs()
                                    ZipInputStream(source.inputStream().buffered()).use { zis ->
                                        var entry = zis.nextEntry
                                        while (entry != null) {
                                            val out = File(dest, entry.name).canonicalFile
                                            require(out.path.startsWith(dest.canonicalPath + File.separator)) { "Unsafe archive entry" }
                                            if (entry.isDirectory) out.mkdirs() else {
                                                out.parentFile?.mkdirs()
                                                out.outputStream().buffered().use { zis.copyTo(it) }
                                            }
                                            zis.closeEntry()
                                            entry = zis.nextEntry
                                        }
                                    }
                                    "Extracted to ${dest.absolutePath}"
                                } else {
                                    val archive = if (dest.isDirectory) File(dest, source.name + ".zip") else dest
                                    archive.parentFile?.mkdirs()
                                    ZipOutputStream(archive.outputStream().buffered()).use { zos ->
                                        val base = if (source.isDirectory) source else source.parentFile ?: source
                                        val list = if (source.isDirectory) source.walkTopDown().filter { it.isFile }.take(10000).toList() else listOf(source)
                                        list.forEach { file ->
                                            val name = if (source.isDirectory) file.relativeTo(base).invariantSeparatorsPath else file.name
                                            zos.putNextEntry(ZipEntry(name))
                                            file.inputStream().buffered().use { it.copyTo(zos) }
                                            zos.closeEntry()
                                        }
                                    }
                                    "ZIP created: ${archive.absolutePath}"
                                }
                            }.getOrElse { "Operation failed: ${it.message}" }
                        }
                        busy = false
                    }
                }) { Text(if (busy) "Working…" else "Create ZIP / Extract ZIP") }
                Text("Tip: To extract, set the source path to a .zip file. To create, set it to a file or folder.", color = TextMuted, fontSize = 10.sp)
            }
            "Duplicates", "Large Files" -> {
                Text(if (feature.title == "Duplicates") "Find exact duplicate files by SHA-256." else "Find files larger than 50 MB.", color = TextMuted, fontSize = 12.sp)
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val found = withContext(Dispatchers.IO) {
                            val all = root.walkTopDown().onEnter { !it.absolutePath.contains("/Android/") }.filter { it.isFile }.take(15000).toList()
                            if (feature.title == "Large Files") all.filter { it.length() >= 50L * 1024 * 1024 }.sortedByDescending { it.length() }.take(150)
                            else {
                                val hashes = all.filter { it.length() > 0 }.groupBy { file ->
                                    runCatching {
                                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                                        file.inputStream().buffered().use { input ->
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) { val n = input.read(buffer); if (n <= 0) break; digest.update(buffer, 0, n) }
                                        }
                                        "${file.length()}:" + digest.digest().joinToString("") { "%02x".format(it) }
                                    }.getOrDefault("error:${file.absolutePath}")
                                }
                                hashes.values.filter { it.size > 1 }.flatten().take(200)
                            }
                        }
                        results = found
                        message = "Scan complete • ${found.size} result(s)"
                        busy = false
                    }
                }) { Text(if (busy) "Scanning storage…" else "Start Scan") }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Blue)
                Text(message, color = Violet, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results, key = { it.absolutePath }) { file ->
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceGlass), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, null, tint = feature.tint)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, color = TextMain, maxLines = 1)
                                    Text(file.parent ?: "", color = TextMuted, fontSize = 9.sp, maxLines = 1)
                                }
                                Text(android.text.format.Formatter.formatShortFileSize(context, file.length()), color = Violet, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            "PDF Tools" -> Text("PDF viewer, image-to-PDF, merge and split are being implemented as a separate module. No fake actions are shown here.", color = TextMuted, fontSize = 12.sp)
            "Secure Vault" -> Text("Encrypted vault is not enabled yet. I won't store private files without proper encryption and recovery handling.", color = TextMuted, fontSize = 12.sp)
            "QR Tools" -> Text("QR scan and generation require camera and QR engine integration; this module is not enabled yet.", color = TextMuted, fontSize = 12.sp)
            else -> Text("This tool is not available yet.", color = TextMuted)
        }
        }
    }
}

@Composable
private fun StorageMetric(label: String, value: String, tint: Color) {
    Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = SurfaceGlass), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextMuted, modifier = Modifier.weight(1f))
            Text(value, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BuiltInMediaViewer(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isVideo = file.extension.lowercase() in listOf("mp4","mkv","webm","3gp","mov")
    val uri = remember(file.absolutePath) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (isVideo) {
                var player by remember(file.absolutePath) { mutableStateOf<VideoView?>(null) }
                var playing by remember { mutableStateOf(true) }
                var controlsVisible by remember { mutableStateOf(true) }
                var duration by remember { mutableIntStateOf(0) }
                var position by remember { mutableIntStateOf(0) }
                var seeking by remember { mutableStateOf(false) }
                var seekPreview by remember { mutableFloatStateOf(0f) }
                var brightness by remember { mutableFloatStateOf(0.55f) }
                var volume by remember { mutableFloatStateOf(0.5f) }
                val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
                val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
                val view = LocalView.current
                val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                LaunchedEffect(player) {
                    while (player != null) {
                        val current = player
                        if (current != null && !seeking) {
                            position = current.currentPosition.coerceAtLeast(0)
                            duration = current.duration.coerceAtLeast(0)
                            playing = current.isPlaying
                        }
                        delay(400)
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).also { video ->
                            player = video
                            video.setVideoURI(uri)
                            video.setOnPreparedListener { media ->
                                duration = media.duration.coerceAtLeast(0)
                                video.start()
                                playing = true
                            }
                            video.setOnCompletionListener { playing = false }
                        }
                    },
                    update = { if (player !== it) player = it }
                )
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(player) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { point ->
                                    val jump = if (point.x < size.width / 2f) -10000 else 10000
                                    val target = ((player?.currentPosition ?: 0) + jump).coerceIn(0, duration.coerceAtLeast(0))
                                    player?.seekTo(target)
                                    position = target
                                    controlsVisible = true
                                }
                            )
                        }
                        .pointerInput(player, brightness, volume) {
                            detectVerticalDragGestures { change, dragAmount ->
                                if (change.position.x < context.resources.displayMetrics.widthPixels / 2f) {
                                    brightness = (brightness - dragAmount / 900f).coerceIn(0.05f, 1f)
                                    dialogWindow?.let { win -> win.attributes = win.attributes.apply { screenBrightness = brightness } }
                                } else {
                                    val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val next = (current - (dragAmount / 45f).toInt()).coerceIn(0, maxVolume)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    volume = next.toFloat() / maxVolume
                                }
                                change.consume()
                                controlsVisible = true
                            }
                        }
                ) {
                    if (controlsVisible) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xD9080B12)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                IconButton(onClick = {
                                    brightness = (brightness - 0.1f).coerceAtLeast(0.05f)
                                    dialogWindow?.let { win -> win.attributes = win.attributes.apply { screenBrightness = brightness } }
                                }) { Icon(Icons.Default.BrightnessLow, null, tint = Color.White) }
                                Text("Brightness ${(brightness * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
                                IconButton(onClick = {
                                    val next = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) - 1).coerceAtLeast(0)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0); volume = next.toFloat() / maxVolume
                                }) { Icon(Icons.Default.VolumeDown, null, tint = Color.White) }
                                Text("Volume ${(volume * 100).toInt()}%", color = TextMuted, fontSize = 10.sp)
                                IconButton(onClick = {
                                    val next = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + 1).coerceAtMost(maxVolume)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0); volume = next.toFloat() / maxVolume
                                }) { Icon(Icons.Default.VolumeUp, null, tint = Color.White) }
                            }
                            Slider(
                                value = if (seeking) seekPreview else if (duration > 0) position.toFloat() / duration else 0f,
                                onValueChange = { seeking = true; seekPreview = it },
                                onValueChangeFinished = {
                                    val target = (seekPreview * duration).toInt().coerceIn(0, duration)
                                    player?.seekTo(target); position = target; seeking = false
                                },
                                colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = TextMuted)
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(String.format("%02d:%02d", position / 60000, (position / 1000) % 60), color = Color.White, fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                Text(String.format("%02d:%02d", duration / 60000, (duration / 1000) % 60), color = TextMuted, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { val p = ((player?.currentPosition ?: 0) - 10000).coerceAtLeast(0); player?.seekTo(p); position = p }) {
                                    Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(24.dp))
                                IconButton(onClick = {
                                    val v = player ?: return@IconButton
                                    if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
                                }, modifier = Modifier.size(54.dp)) {
                                    Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, null, tint = Blue, modifier = Modifier.size(48.dp))
                                }
                                Spacer(Modifier.width(24.dp))
                                IconButton(onClick = { val p = ((player?.currentPosition ?: 0) + 10000).coerceAtMost(duration); player?.seekTo(p); position = p }) {
                                    Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                            }
                            Text("Double tap left/right: −10s / +10s  •  Swipe left/right side: brightness / volume", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x99000000)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    Text(file.name, color = Color.White, maxLines = 1)
                }
            } else {
                var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
                var failed by remember { mutableStateOf(false) }
                LaunchedEffect(file.absolutePath) {
                    bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                            val maxWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
                            var sample = 1
                            while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
                            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        }.getOrNull()
                    }
                    failed = bitmap == null
                }
                if (bitmap != null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { it.setImageBitmap(bitmap) })
                else if (failed) Text("Unable to display image", color = Color.White, modifier = Modifier.align(Alignment.Center))
                else CircularProgressIndicator(color = Blue, modifier = Modifier.align(Alignment.Center))
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x99000000)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    Text(file.name, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}
@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Personalize your ORION experience", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        listOf(
            "Appearance" to "AMOLED • Blue • Violet • Glass",
            "Language" to "বাংলা + English",
            "Privacy" to "Offline-first • On-device files",
            "Permissions" to "Ask only when needed",
            "About" to "ORION FILES v0.1.0"
        ).forEach { (title, desc) ->
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = SurfaceGlass), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, color = TextMain, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
