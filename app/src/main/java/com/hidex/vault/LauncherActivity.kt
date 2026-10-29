package com.hidex.vault

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LauncherBg = Color(0xFF080C18)
private val LauncherCard = Color(0xFF151D32)
private val LauncherPurple = Color(0xFF8B7CFF)
private val LauncherMint = Color(0xFF42D9C8)
private val LauncherMuted = Color(0xFFAAB5D0)

private data class LaunchableApp(val label: String, val packageName: String)

class LauncherActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hidex_launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = LauncherPurple,
                secondary = LauncherMint,
                background = LauncherBg,
                surface = LauncherCard,
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
            if (packageName == packageNameForSelf()) return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: activityInfo.name.substringAfterLast('.')
            LaunchableApp(label, packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun packageNameForSelf() = applicationContext.packageName

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

        Scaffold(
            containerColor = LauncherBg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (manageMode) "HideX • Manage apps" else "HideX Launcher", fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (manageMode) "Select apps to hide from this drawer" else "${visibleApps.size} apps shown",
                                style = MaterialTheme.typography.bodySmall,
                                color = LauncherMuted
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            manageMode = !manageMode
                            search = ""
                        }) {
                            Icon(
                                if (manageMode) Icons.Default.Apps else Icons.Default.Settings,
                                contentDescription = if (manageMode) "Open app drawer" else "Manage hidden apps",
                                tint = LauncherMint
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LauncherBg)
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    label = { Text("Search apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                if (manageMode) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LauncherCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Hide selected apps", fontWeight = FontWeight.Bold)
                            Text(
                                "Hidden apps disappear only from HideX Launcher. They remain installed and may still appear in other launchers or Settings.",
                                color = LauncherMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                prefs.edit().putStringSet("hidden_packages", hidden.toSet()).apply()
                                manageMode = false
                                search = ""
                                refresh++
                            }) { Text("Done / Save", color = LauncherMint) }
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(managedApps, key = { it.packageName }) { app ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = LauncherCard),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        hidden = if (app.packageName in hidden) hidden - app.packageName else hidden + app.packageName
                                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppMark(app.label)
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    Checkbox(
                                        checked = app.packageName in hidden,
                                        onCheckedChange = { checked ->
                                            hidden = if (checked) hidden + app.packageName else hidden - app.packageName
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (visibleApps.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Apps, null, tint = LauncherPurple, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("No apps to show", fontWeight = FontWeight.Bold)
                                Text("Tap the settings icon to manage hidden apps.", color = LauncherMuted)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = LauncherCard),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            val launch = packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launch != null) startActivity(launch)
                                        }.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppMark(app.label)
                                        Spacer(Modifier.width(12.dp))
                                        Text(app.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Text("Open", color = LauncherMint, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppMark(label: String) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(13.dp),
            color = LauncherPurple.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "A",
                    color = LauncherMint,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
