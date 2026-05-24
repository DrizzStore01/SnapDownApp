package com.zyro.snapdown

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zyro.snapdown.ui.theme.SnapDownTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ============================================================
// Entry Point
// ============================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SnapDownTheme { SnapDownApp() } }
    }
}

// ============================================================
// Data Models
// ============================================================
sealed class Platform {
    object TikTok : Platform()
    object YouTube : Platform()
}

data class DownloadItem(
    val id: String,
    val url: String,
    val label: String,
    val badge: String = "",
    val isAudio: Boolean = false
)

data class FetchResult(
    val title: String,
    val author: String,
    val thumbnail: String,
    val platform: Platform,
    val downloads: List<DownloadItem>
)

// ============================================================
// Network helpers
// ============================================================
private fun httpGet(urlStr: String): String {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 15000
    conn.readTimeout = 20000
    return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
}

suspend fun fetchTikTok(ttUrl: String): Result<FetchResult> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(ttUrl.trim(), "UTF-8")
        val json = JSONObject(httpGet("https://api.nexray.eu.cc/downloader/tiktok?url=$encoded"))

        if (!json.optBoolean("status", false)) throw Exception("API returned error status")

        val result = json.getJSONObject("result")
        val authorObj = result.optJSONObject("author")
        val musicInfo = result.optJSONObject("music_info")

        val downloads = mutableListOf<DownloadItem>()
        val videoUrl = result.optString("data", "").ifBlank { result.optString("video_url", "") }
        if (videoUrl.isNotBlank()) {
            downloads.add(DownloadItem("tt-vid", videoUrl, "Video (Tanpa WM)", isAudio = false))
        }
        val audioUrl = musicInfo?.optString("url", "") ?: ""
        if (audioUrl.isNotBlank()) {
            downloads.add(DownloadItem("tt-aud", audioUrl, "Audio (MP3)", isAudio = true))
        }

        Result.success(
            FetchResult(
                title = result.optString("title", "Video TikTok"),
                author = authorObj?.optString("nickname", "TikTok User") ?: "TikTok User",
                thumbnail = result.optString("cover", ""),
                platform = Platform.TikTok,
                downloads = downloads
            )
        )
    } catch (e: Exception) { Result.failure(e) }
}

private fun extractYtId(url: String): String? {
    val regex = Regex("(?:youtu\\.be/|youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/)([^\"&?/\\s]{11})")
    return regex.find(url)?.groupValues?.get(1)
}

suspend fun fetchYouTube(ytUrl: String, resolution: String): Result<FetchResult> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(ytUrl.trim(), "UTF-8")

        var mp4Result: JSONObject? = null
        var mp3Result: JSONObject? = null

        try {
            val r = JSONObject(httpGet("https://api.nexray.eu.cc/downloader/v1/ytmp4?url=$encoded&resolusi=$resolution"))
            if (r.optBoolean("status", false)) mp4Result = r.optJSONObject("result")
        } catch (_: Exception) {}

        try {
            val r = JSONObject(httpGet("https://api.nexray.eu.cc/downloader/v1/ytmp3?url=$encoded"))
            if (r.optBoolean("status", false)) mp3Result = r.optJSONObject("result")
        } catch (_: Exception) {}

        if (mp4Result == null && mp3Result == null) throw Exception("Gagal mengambil data dari server")

        val mainResult = mp4Result ?: mp3Result!!

        // Smart thumbnail fallback (same as web)
        var thumb = mainResult.optString("thumbnail", "")
        if (thumb.isBlank()) {
            val ytId = extractYtId(ytUrl)
            if (ytId != null) thumb = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
        }

        val downloads = mutableListOf<DownloadItem>()
        mp4Result?.optString("url", "")?.takeIf { it.isNotBlank() }?.let {
            downloads.add(DownloadItem("yt-vid", it, "Video (MP4)", mp4Result.optString("quality", "${resolution}p"), false))
        }
        mp3Result?.optString("url", "")?.takeIf { it.isNotBlank() }?.let {
            downloads.add(DownloadItem("yt-aud", it, "Audio (MP3)", mp3Result.optString("quality", "320k"), true))
        }

        Result.success(
            FetchResult(
                title = mainResult.optString("title", "Video YouTube"),
                author = mainResult.optString("author", "YouTube Creator"),
                thumbnail = thumb,
                platform = Platform.YouTube,
                downloads = downloads
            )
        )
    } catch (e: Exception) { Result.failure(e) }
}

fun enqueueDownload(context: Context, url: String, title: String, isAudio: Boolean) {
    val ext = if (isAudio) "mp3" else "mp4"
    val safe = title.take(40).replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    val filename = "SnapDown_${safe}_${System.currentTimeMillis()}.$ext"
    val dir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS

    DownloadManager.Request(Uri.parse(url))
        .setTitle("SnapDown — $title")
        .setDescription(if (isAudio) "Mengunduh audio..." else "Mengunduh video...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(dir, filename)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .also { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(it) }
}

// ============================================================
// Color constants (local to composable layer)
// ============================================================
private val BgDark       = Color(0xFF0F172A)
private val SurfaceDark  = Color(0x661E293B)
private val SurfaceSolid = Color(0xFF1E293B)
private val Border       = Color(0x0DFFFFFF)
private val Border2      = Color(0xFF334155)
private val TextPri      = Color(0xFFF8FAFC)
private val TextSec      = Color(0xFF94A3B8)
private val TextMuted    = Color(0xFF64748B)
private val Blue         = Color(0xFF3B82F6)
private val Indigo       = Color(0xFF4F46E5)
private val PurpleAcc    = Color(0xFF9333EA)
private val CyanAcc      = Color(0xFF22D3EE)
private val RedAcc       = Color(0xFFEF4444)
private val GreenAcc     = Color(0xFF4ADE80)

// ============================================================
// Root Composable
// ============================================================
@Composable
fun SnapDownApp() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var activePlatform  by remember { mutableStateOf<Platform>(Platform.TikTok) }
    var urlInput        by remember { mutableStateOf("") }
    var resolution      by remember { mutableStateOf("1080") }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var fetchResult     by remember { mutableStateOf<FetchResult?>(null) }
    var downloadedIds   by remember { mutableStateOf(setOf<String>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Subtle radial accent top-right (mirrors web background-image)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33213A63), Color.Transparent),
                        center = Offset.Unspecified,
                        radius = 600f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Header ──
            AppHeader()

            Spacer(Modifier.height(28.dp))

            // ── Hero badge ──
            HeroBadge()

            Spacer(Modifier.height(20.dp))

            // ── Platform Tabs ──
            PlatformTabs(
                active = activePlatform,
                onSelect = {
                    activePlatform = it
                    urlInput = ""; errorMessage = null
                    fetchResult = null; downloadedIds = emptySet()
                }
            )

            Spacer(Modifier.height(16.dp))

            // ── Input Card ──
            InputCard(
                urlInput = urlInput,
                onUrlChange = { urlInput = it; errorMessage = null },
                platform = activePlatform,
                resolution = resolution,
                onResolutionChange = { resolution = it },
                isLoading = isLoading,
                errorMessage = errorMessage,
                onFetch = {
                    scope.launch {
                        val url = urlInput.trim()
                        if (url.isBlank()) { errorMessage = "URL tidak boleh kosong"; return@launch }
                        if (activePlatform is Platform.TikTok && !url.contains("tiktok")) {
                            errorMessage = "Tautan TikTok tidak valid!"; return@launch
                        }
                        if (activePlatform is Platform.YouTube &&
                            !(url.contains("youtube") || url.contains("youtu.be"))) {
                            errorMessage = "Tautan YouTube tidak valid!"; return@launch
                        }

                        isLoading = true; errorMessage = null
                        fetchResult = null; downloadedIds = emptySet()

                        val result = when (activePlatform) {
                            is Platform.TikTok  -> fetchTikTok(url)
                            is Platform.YouTube -> fetchYouTube(url, resolution)
                        }

                        isLoading = false
                        result.fold(
                            onSuccess = { fetchResult = it },
                            onFailure = { errorMessage = "Gagal: ${it.message}" }
                        )
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // ── Result / Skeleton ──
            AnimatedVisibility(
                visible = isLoading || fetchResult != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                exit  = fadeOut()
            ) {
                if (isLoading) ResultSkeleton()
                else fetchResult?.let { result ->
                    ResultCard(
                        result = result,
                        downloadedIds = downloadedIds,
                        onDownload = { item ->
                            enqueueDownload(context, item.url, result.title, item.isAudio)
                            downloadedIds = downloadedIds + item.id
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            AppFooter()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
// Header
// ============================================================
@Composable
fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    Brush.linearGradient(listOf(Blue, PurpleAcc)),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = 20.sp) }

        Spacer(Modifier.width(10.dp))

        Text("Snap", color = TextPri,         fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Down", color = Color(0xFF60A5FA), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ============================================================
// Hero badge  ("Server Tercepat 2026")
// ============================================================
@Composable
fun HeroBadge() {
    Row(
        modifier = Modifier
            .background(SurfaceDark, RoundedCornerShape(50.dp))
            .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(50.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ping dot
        val pulse = rememberInfiniteTransition(label = "ping")
        val pingAlpha by pulse.animateFloat(
            initialValue = 0.2f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ping_alpha"
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Blue.copy(alpha = pingAlpha), RoundedCornerShape(50))
        )
        Text("Server Unduhan Tercepat 2026", color = TextSec, fontSize = 13.sp)
    }
}

// ============================================================
// Platform Tabs
// ============================================================
@Composable
fun PlatformTabs(active: Platform, onSelect: (Platform) -> Unit) {
    Row(
        modifier = Modifier
            .background(SurfaceSolid, RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabBtn("🎵 TikTok",  active is Platform.TikTok,  CyanAcc) { onSelect(Platform.TikTok) }
        TabBtn("▶️ YouTube", active is Platform.YouTube, RedAcc)  { onSelect(Platform.YouTube) }
    }
}

@Composable
fun TabBtn(label: String, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Border2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color     = if (isActive) TextPri else TextMuted,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            fontSize  = 14.sp
        )
    }
}

// ============================================================
// Input Card
// ============================================================
val resOptions = listOf("360", "480", "720", "1080", "1440", "2160")

@Composable
fun InputCard(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    platform: Platform,
    resolution: String,
    onResolutionChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onFetch: () -> Unit
) {
    val accentColor = if (platform is Platform.TikTok) CyanAcc else RedAcc
    val placeholder = if (platform is Platform.TikTok)
        "Tempel tautan TikTok di sini..." else "Tempel tautan YouTube di sini..."

    var showDrop by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Border), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Accent bar + URL input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = errorMessage != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = accentColor,
                    unfocusedBorderColor    = Border2,
                    errorBorderColor        = RedAcc,
                    focusedTextColor        = TextPri,
                    unfocusedTextColor      = TextPri,
                    cursorColor             = accentColor,
                    focusedContainerColor   = Color(0x801E293B),
                    unfocusedContainerColor = Color(0x801E293B),
                    errorContainerColor     = Color(0x801E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Text("🔗", fontSize = 16.sp) }
            )
        }

        if (errorMessage != null) {
            Text(errorMessage, color = RedAcc, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Resolution dropdown — YouTube only
            if (platform is Platform.YouTube) {
                Box(modifier = Modifier.width(116.dp)) {
                    OutlinedButton(
                        onClick = { showDrop = !showDrop },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        border = BorderStroke(1.dp, Border2),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0x801E293B),
                            contentColor   = TextPri
                        )
                    ) {
                        Text(
                            text = if (resolution == "2160") "4K" else "${resolution}p",
                            fontWeight = FontWeight.Medium,
                            fontSize   = 14.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (showDrop) "▲" else "▼",
                            color = TextSec,
                            fontSize = 10.sp
                        )
                    }
                    DropdownMenu(
                        expanded = showDrop,
                        onDismissRequest = { showDrop = false },
                        modifier = Modifier.background(SurfaceSolid)
                    ) {
                        resOptions.forEach { res ->
                            val lbl = if (res == "2160") "2160p (4K)" else "${res}p"
                            val isSelected = res == resolution
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        lbl,
                                        color = if (isSelected) RedAcc else Color(0xFFCBD5E1),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = { onResolutionChange(res); showDrop = false },
                                modifier = if (isSelected) Modifier.background(Color(0x1AEF4444)) else Modifier
                            )
                        }
                    }
                }
            }

            // Fetch Button
            Button(
                onClick = onFetch,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor         = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (!isLoading)
                                Brush.horizontalGradient(listOf(Color(0xFF2563EB), Indigo))
                            else
                                Brush.horizontalGradient(listOf(Color(0xFF1E3A5F), Color(0xFF312A6E))),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TextPri)
                            Text("Memproses...", color = TextPri, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Ambil Video", color = TextPri, fontWeight = FontWeight.SemiBold)
                            Text("→", color = TextPri, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Skeleton  (shimmer loader)
// ============================================================
@Composable
fun ResultSkeleton() {
    val inf = rememberInfiniteTransition(label = "sk")
    val x by inf.animateFloat(
        initialValue = -600f,
        targetValue  = 600f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "sx"
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1E293B)),
        start  = Offset(x, 0f),
        end    = Offset(x + 600f, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Border), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(shimmer, RoundedCornerShape(12.dp)))
        Box(Modifier.fillMaxWidth(0.75f).height(20.dp).background(shimmer, RoundedCornerShape(6.dp)))
        Box(Modifier.fillMaxWidth(0.5f).height(14.dp).background(shimmer, RoundedCornerShape(6.dp)))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(60.dp).background(shimmer, RoundedCornerShape(12.dp)))
        Box(Modifier.fillMaxWidth().height(60.dp).background(shimmer, RoundedCornerShape(12.dp)))
    }
}

// ============================================================
// Result Card
// ============================================================
@Composable
fun ResultCard(
    result: FetchResult,
    downloadedIds: Set<String>,
    onDownload: (DownloadItem) -> Unit
) {
    val platformEmoji = if (result.platform is Platform.TikTok) "🎵" else "▶️"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Border), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail
        if (result.thumbnail.isNotBlank()) {
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model            = result.thumbnail,
                    contentDescription = "Thumbnail",
                    modifier         = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale     = ContentScale.Crop
                )
                // Platform badge top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xCC0F172A), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(platformEmoji, fontSize = 14.sp) }
            }
        }

        // Title
        Text(
            text = result.title,
            color = TextPri,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("👤", fontSize = 12.sp)
            Text(result.author, color = TextSec, fontSize = 13.sp)
        }

        HorizontalDivider(color = Color(0x1AFFFFFF))

        // Download buttons
        result.downloads.forEach { item ->
            DownloadButton(
                item = item,
                downloaded = item.id in downloadedIds,
                onClick = { onDownload(item) }
            )
        }
    }
}

@Composable
fun DownloadButton(item: DownloadItem, downloaded: Boolean, onClick: () -> Unit) {
    val btnColor = if (item.isAudio) PurpleAcc else Blue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSolid)
            .border(BorderStroke(1.dp, Border2), RoundedCornerShape(14.dp))
            .clickable(enabled = !downloaded, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon box
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(btnColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Text(if (item.isAudio) "🎵" else "🎬", fontSize = 20.sp) }

        // Label + badge
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.label, color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (item.badge.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(btnColor.copy(0.15f), RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, btnColor.copy(0.3f)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(item.badge, color = btnColor, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                }
            }
            Text(
                text  = if (downloaded) "✅ Download dimulai! Cek folder Downloads." else "Tap untuk download",
                color = if (downloaded) GreenAcc else TextMuted,
                fontSize = 12.sp
            )
        }

        // Download circle button
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (downloaded) Color(0xFF166534) else Border2, RoundedCornerShape(50.dp)),
            contentAlignment = Alignment.Center
        ) { Text(if (downloaded) "✓" else "⬇", color = TextPri, fontSize = 14.sp) }
    }
}

// ============================================================
// Footer
// ============================================================
@Composable
fun AppFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x331E293B), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Border), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Brush.linearGradient(listOf(Blue, PurpleAcc)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("⚡", fontSize = 16.sp) }
            Column {
                Row {
                    Text("Snap", color = TextPri,          fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Down", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("Universal Downloader", color = TextMuted, fontSize = 11.sp)
            }
        }
        Text("by Nexray & Gemini", color = TextMuted, fontSize = 12.sp)
    }
}
