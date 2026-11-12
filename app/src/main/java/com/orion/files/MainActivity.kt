package com.orion.files

import android.os.Bundle
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider

private val Black = Color(0xFF030407)
private val SurfaceGlass = Color(0xB8171B2B)
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
                .background(Brush.verticalGradient(listOf(Color(0xFF080D1A), Black, Black)))
        ) {
            AnimatedContent(
                targetState = Triple(tab, selectedFeature, searchOpen),
                transitionSpec = {
                    (fadeIn(tween(170)) + slideInHorizontally(tween(170)) { it / 14 }) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(110)) { -it / 18 })
                },
                label = "page-transition"
            ) { state ->
                when {
                    state.second != null -> FeatureDetail(state.second!!)
                    state.first == 0 -> HomeScreen(
                        onSearch = { searchOpen = !searchOpen },
                        searchOpen = searchOpen,
                        query = query,
                        onQuery = { query = it },
                        onFeature = { feature -> if (feature.title == "All Files") tab = 1 else selectedFeature = feature }
                    )
                    state.first == 1 -> FileBrowserScreen(hasAllFilesAccess, selectedFolderUri, onRequestAllFiles = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                            runCatching { allFilesSettings.launch(intent) }.onFailure {
                                allFilesSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    }, onPickFolder = { folderPicker.launch(null) })
                    state.first == 2 -> FeatureList("Smart Tools", "Useful tools in one place", features.drop(4), onFeature = { selectedFeature = it })
                    else -> SettingsScreen()
                }
            }
        }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ORION", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("FILES  •  ALL YOUR FILES. ONE PLACE.", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMain)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Blue, Violet))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White) }
        }
        AnimatedVisibility(searchOpen, enter = fadeIn() + androidx.compose.animation.expandVertically(), exit = fadeOut() + androidx.compose.animation.shrinkVertically()) {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("Search files and tools…", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue,
                    unfocusedBorderColor = BorderGlow,
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain
                )
            )
        }
        Spacer(Modifier.height(20.dp))
        StorageCard()
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick Access", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("12 tools", color = Violet, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(features.filter { it.title.contains(query, true) || query.isBlank() }) { feature ->
                FeatureCard(feature, onClick = { onFeature(feature) })
            }
        }
    }
}

@Composable
private fun StorageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0x663D7EFF), Color(0x553F216E), Color(0x33101828)))).padding(18.dp)) {
            // Soft blurred accent glow behind the translucent glass panel.
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp)
                    .size(34.dp)
                    .background(Violet.copy(alpha = 0.20f), CircleShape)
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DonutLarge, contentDescription = null, tint = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("Device Storage", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("Overview", color = Violet, fontSize = 11.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("--", color = TextMain, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("  Storage scan not connected yet", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0xFF252B3B))) {
                    Box(Modifier.fillMaxWidth(0.42f).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Blue, Violet))).alpha(0.9f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Storage information will appear after file access is implemented.", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(112.dp)
            .animateContentSize(animationSpec = tween(160))
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
    onPickFolder: () -> Unit
) {
    val context = LocalContext.current
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
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
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
                            if (file.isDirectory) currentFolder = file else {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }.onFailure { android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show() }
                            }
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
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text(title, color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        list.forEach { FeatureCard(it) { onFeature(it) }; Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun FeatureDetail(feature: Feature) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(82.dp).clip(RoundedCornerShape(26.dp)).background(feature.tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(feature.icon, contentDescription = null, tint = feature.tint, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(feature.title, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("This module is planned for the ORION FILES feature build.", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Surface(color = Color(0x332F70E8), shape = RoundedCornerShape(50.dp)) {
            Text("COMING IN FEATURE RELEASE", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
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
