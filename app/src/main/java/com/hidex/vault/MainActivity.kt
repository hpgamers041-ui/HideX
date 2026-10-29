package com.hidex.vault

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val Ink = Color(0xFF080C18)
private val SurfaceDark = Color(0xFF151D32)
private val Purple = Color(0xFF8B7CFF)
private val Mint = Color(0xFF42D9C8)
private val SoftText = Color(0xFFAAB5D0)

data class VaultEntry(val id: String, val name: String, val bytes: Long)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hidex_vault", MODE_PRIVATE) }
    private var vaultKey: ByteArray? = null
    private val vaultDir by lazy { File(filesDir, "vault").apply { mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Purple, secondary = Mint, background = Ink, surface = SurfaceDark,
                onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White
            )) {
                HideXScreen(
                    hasPin = prefs.contains("salt") && prefs.contains("check"),
                    entriesProvider = { entries() },
                    onSetPin = { value -> setPin(value) },
                    onUnlock = { value -> unlock(value) },
                    onImport = { uri -> importFile(uri) },
                    onExport = { id -> exportBytes(id) },
                    onDelete = { id -> deleteEntry(id) }
                )
            }
        }
    }


    private fun setPin(pin: String): Boolean {
        if (pin.length !in 6..12 || !pin.all(Char::isDigit)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt)
        val check = MessageDigest.getInstance("SHA-256").digest(key + "HideX-verifier".toByteArray())
        prefs.edit().putString("salt", b64(salt)).putString("check", b64(check)).apply()
        vaultKey = key
        return true
    }

    private fun unlock(pin: String): Boolean {
        if (pin.length !in 6..12 || !pin.all(Char::isDigit)) return false
        return try {
            val salt = unb64(prefs.getString("salt", "") ?: "")
            val expected = unb64(prefs.getString("check", "") ?: "")
            val key = deriveKey(pin, salt)
            val actual = MessageDigest.getInstance("SHA-256").digest(key + "HideX-verifier".toByteArray())
            if (MessageDigest.isEqual(expected, actual)) {
                vaultKey?.fill(0)
                vaultKey = key
                true
            } else {
                key.fill(0)
                false
            }
        } catch (_: Exception) { false }
    }

    private fun deriveKey(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun importFile(uri: Uri): String? {
        val key = vaultKey ?: return "Unlock the vault first."
        return try {
            val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString) else null
            } ?: "private-file"
            val plain = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return "Could not read the selected file."
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val encrypted = nonce + cipher.doFinal(plain)
            plain.fill(0)
            val id = java.util.UUID.randomUUID().toString()
            File(vaultDir, "$id.hxv").writeBytes(encrypted)
            val list = readMetadata()
            list.put(JSONObject().put("id", id).put("name", name))
            prefs.edit().putString("entries", list.toString()).apply()
            null
        } catch (e: Exception) { "Import failed: ${e.message ?: "unknown error"}" }
    }

    private fun exportBytes(id: String): ByteArray? {
        val key = vaultKey ?: return null
        return try {
            val file = File(vaultDir, "$id.hxv")
            val raw = file.readBytes()
            if (raw.size < 13) return null
            val nonce = raw.copyOfRange(0, 12)
            val encrypted = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.doFinal(encrypted)
        } catch (_: Exception) { null }
    }

    private fun entries(): List<VaultEntry> {
        val meta = readMetadata()
        return (0 until meta.length()).mapNotNull { i ->
            val o = meta.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val f = File(vaultDir, "$id.hxv")
            if (!f.isFile) null else VaultEntry(id, o.optString("name", "Private file"), f.length())
        }.reversed()
    }

    private fun readMetadata(): JSONArray = try {
        JSONArray(prefs.getString("entries", "[]") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    private fun deleteEntry(id: String) {
        File(vaultDir, "$id.hxv").delete()
        val old = readMetadata()
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("id") != id) next.put(item)
        }
        prefs.edit().putString("entries", next.toString()).apply()
    }

    private fun b64(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun unb64(value: String) = Base64.decode(value, Base64.NO_WRAP)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HideXScreen(
        hasPin: Boolean,
        entriesProvider: () -> List<VaultEntry>,
        onSetPin: (String) -> Boolean,
        onUnlock: (String) -> Boolean,
        onImport: (Uri) -> String?,
        onExport: (String) -> ByteArray?,
        onDelete: (String) -> Unit
    ) {
        var unlocked by remember { mutableStateOf(false) }
        var pin by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var refresh by remember { mutableIntStateOf(0) }
        var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
        val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && unlocked) {
                message = onImport(uri) ?: "File encrypted and saved to your private vault."
                refresh++
            }
        }
        val pickExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val target = pendingExport
            if (uri != null && target != null && unlocked) {
                val bytes = onExport(target.first)
                message = try {
                    if (bytes == null) "Could not decrypt file. Check the PIN and file integrity."
                    else {
                        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        bytes.fill(0)
                        "File exported. Keep it in a safe place."
                    }
                } catch (e: Exception) { "Export failed: ${e.message ?: "unknown error"}" }
                pendingExport = null
            }
        }
        val entries = remember(refresh, unlocked) { if (unlocked) entriesProvider() else emptyList() }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1A3A), Ink, Ink)))) {
            if (!unlocked) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(82.dp).background(Purple.copy(alpha=.2f), RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, null, tint = Purple, modifier = Modifier.size(45.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("HideX", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text(if (hasPin) "Your encrypted private vault" else "Create your private vault", color = SoftText)
                    Spacer(Modifier.height(28.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (hasPin) "Unlock HideX" else "Set a PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(if (hasPin) "Enter your 6–12 digit PIN to decrypt your vault key." else "Choose a PIN with 6–12 digits. It derives the encryption key for your files.", color = SoftText)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12); message = "" }, label = { Text("PIN (6–12 digits)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                            if (message.isNotBlank()) Text(message, color = Color(0xFFFF8B9B), modifier = Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                if (pin.length !in 6..12) message = "Enter a PIN with 6–12 digits."
                                else {
                                    val ok = if (hasPin) onUnlock(pin) else onSetPin(pin)
                                    if (ok) { unlocked = true; pin = ""; message = "" }
                                    else message = "Incorrect PIN. Try again."
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text(if (hasPin) "Unlock vault" else "Create vault") }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Your files are encrypted locally with AES-GCM.", color = SoftText, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("HideX", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                            Text("Your encrypted private space", color = SoftText)
                        }
                        IconButton(onClick = { vaultKey?.fill(0); vaultKey = null; unlocked = false; refresh++ }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock vault", tint = Mint)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Encrypted vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${entries.size} private file(s)", color = SoftText)
                            }
                            Button(onClick = { pickImport.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Add file") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (message.isNotBlank()) {
                        Text(message, color = Mint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Lock, null, tint = Purple, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Your vault is empty", fontWeight = FontWeight.Bold)
                                Text("Tap Add file to encrypt a file into HideX.", color = SoftText)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            items(entries, key = { it.id }) { entry ->
                                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FilePresent, null, tint = Mint, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(formatSize(entry.bytes), color = SoftText, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { pendingExport = entry.id to entry.name; pickExport.launch(entry.name) }) { Text("Export") }
                                        IconButton(onClick = { onDelete(entry.id); refresh++; message = "Encrypted file deleted." }) { Icon(Icons.Default.Delete, contentDescription = "Delete encrypted file", tint = Color(0xFFFF8B9B)) }
                                    }
                                }
                            }
                        }
                    }
                    Text("Files stay in HideX's private app storage. Exported copies are no longer protected by HideX.", color = SoftText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
