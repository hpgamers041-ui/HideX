package com.hidex.vault

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A premium HyperOS-inspired style, built with original HideX branding.
private val HomeBg = Color(0xFF10131D)
private val Glass = Color(0xFF202638)
private val GlassLight = Color(0xFF2A3348)
private val Accent = Color(0xFFFF6B5F)
private val Cyan = Color(0xFF8CE7E0)
private val Muted = Color(0xFFB5BDCD)

private data class LaunchableApp(val label: String, val packageName: String)

class LauncherActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hidex_launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent,
                secondary = Cyan,
                background = HomeBg,
                surface = Glass,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                LauncherScreen()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            if (packageName == applicationContext.packageName) return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: activityInfo.name.substringAfterLast('.')
            LaunchableApp(label, packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LauncherScreen() {
        var manageMode by remember { mutableStateOf(false) }
        var search by remember { mutableStateOf("") }
        var refresh by remember { mutableIntStateOf(0) }
        var hidden by remember {
            mutableStateOf(prefs.getStringSet("hidden_packages", emptySet())?.toSet() ?: emptySet())
        }
        val allApps = remember(refresh) { getLaunchableApps() }
        val visibleApps = allApps.filter {
            it.packageName !in hidden && it.label.contains(search, ignoreCase = true)
        }
        val managedApps = allApps.filter { it.label.contains(search, ignoreCase = true) }
        val date = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF29364B), HomeBg, Color(0xFF111522)))
            )
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (manageMode) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Manage apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Choose what appears in HideX", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { manageMode = false; search = "" }) {
                            Icon(Icons.Default.Apps, contentDescription = "Back to home", tint = Cyan)
                        }
                    }
                    SearchField(search, { search = it })
                    Text(
                        "Hidden apps stay installed. This only removes them from the HideX home screen.",
                        color = Muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(managedApps, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .background(Glass).clickable {
                                        hidden = if (app.packageName in hidden) hidden - app.packageName else hidden + app.packageName
                                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app.label, 44)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Checkbox(
                                    checked = app.packageName in hidden,
                                    onCheckedChange = { checked ->
                                        hidden = if (checked) hidden + app.packageName else hidden - app.packageName
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            prefs.edit().putStringSet("hidden_packages", hidden.toSet()).apply()
                            manageMode = false
                            search = ""
                            refresh++
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Save changes", fontWeight = FontWeight.Bold) }
                } else {
                    // Home header
                    Row(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Hello there", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            Text("HideX", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                            Text(date, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(
                            shape = CircleShape,
                            color = GlassLight,
                            modifier = Modifier.size(48.dp).clickable { manageMode = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, contentDescription = "Customize launcher", tint = Cyan)
                            }
                        }
                    }

                    // Premium-style info widget
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF3C4D67), Color(0xFF252D42), Color(0xFF3A2F3D))))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR SPACE", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Everything,\nin one place.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("${visibleApps.size} apps ready", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(shape = CircleShape, color = Accent.copy(alpha = 0.18f), modifier = Modifier.size(68.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = Accent, modifier = Modifier.size(34.dp))
                            }
                        }
                    }

                    SearchField(search, { search = it })

                    Row(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("A–Z  ·  ${visibleApps.size}", color = Muted, style = MaterialTheme.typography.labelMedium)
                    }

                    if (visibleApps.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = Accent, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("No apps found", fontWeight = FontWeight.Bold)
                                Text("Try another search or manage hidden apps.", color = Muted, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(visibleApps.chunked(4)) { rowApps ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rowApps.forEach { app ->
                                        Column(
                                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                                .clickable {
                                                    packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                                                }.padding(vertical = 8.dp, horizontal = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            AppIcon(app.label, 54)
                                            Spacer(Modifier.height(7.dp))
                                            Text(
                                                app.label, color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                textAlign = TextAlign.Center, maxLines = 2,
                                                minLines = 1
                                            )
                                        }
                                    }
                                    repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    // Floating dock
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(26.dp)).background(Color(0xCC252D40))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DockItem("Apps", "A") { search = ""; manageMode = false }
                        DockItem("Search", "⌕") { /* Search field is ready above. */ }
                        DockItem("Hide", "H") { manageMode = true }
                        DockItem("Settings", "⚙") { manageMode = true }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchField(value: String, onValueChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            placeholder = { Text("Search apps", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Cyan) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                focusedContainerColor = Glass.copy(alpha = 0.85f),
                unfocusedContainerColor = Glass.copy(alpha = 0.75f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
    }

    @Composable
    private fun AppIcon(label: String, size: Int) {
        val colors = listOf(
            Color(0xFFFF796F), Color(0xFF7C8DFF), Color(0xFF47C8B7),
            Color(0xFFFFB95E), Color(0xFFB48CFF), Color(0xFF56A9E8)
        )
        val colorIndex = (label.hashCode().toUInt().toLong() % colors.size).toInt()
        Surface(
            modifier = Modifier.size(size.dp),
            shape = RoundedCornerShape(if (size > 48) 19.dp else 15.dp),
            color = colors[colorIndex].copy(alpha = 0.20f)
        ) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(colors[colorIndex].copy(alpha = 0.32f), GlassLight))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.firstOrNull()?.uppercase(Locale.getDefault()) ?: "A",
                    color = Color.White, fontWeight = FontWeight.ExtraBold,
                    style = if (size > 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    @Composable
    private fun DockItem(label: String, symbol: String, onClick: () -> Unit) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(symbol, color = Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
