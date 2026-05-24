package com.zyro.snapdown

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ─────────────────────────────────────────────
// COLORS (matching web dark theme)
// ─────────────────────────────────────────────
private val NavyBg     = Color(0xFF0F172A)
private val NavyCard   = Color(0xFF1E293B)
private val NavyBorder = Color(0xFF334155)
private val Blue400    = Color(0xFF60A5FA)
private val Blue500    = Color(0xFF3B82F6)
private val Blue600    = Color(0xFF2563EB)
private val Indigo600  = Color(0xFF4F46E5)
private val Purple500  = Color(0xFFA855F7)
private val CyanAcc    = Color(0xFF4FACFE)
private val RedYT      = Color(0xFFEF4444)
private val GrayText   = Color(0xFF94A3B8)
private val SnapWhite  = Color(0xFFF8FAFC)

// ─────────────────────────────────────────────
// MODELS
// ─────────────────────────────────────────────
enum class Platform { TIKTOK, YOUTUBE }

data class DLOption(
    val id: String,
    val url: String,
    val label: String,
    val badge: String,
    val isVideo: Boolean
)

data class VideoResult(
    val title: String,
    val author: String,
    val thumbnail: String,
    val downloads: List<DLOption>,
    val platform: Platform
)

// ─────────────────────────────────────────────
// API
// ─────────────────────────────────────────────
suspend fun fetchTikTok(url: String): Result<VideoResult> = withContext(Dispatchers.IO) {
    try {
        val apiUrl = "https://api.nexray.eu.cc/downloader/tiktok?url=${URLEncoder.encode(url, "UTF-8")}"
        val conn = URL(apiUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        val json = JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() })
        if (!json.optBoolean("status")) throw Exception("API false")
        val r = json.getJSONObject("result")
        val downloads = mutableListOf<DLOption>()
        (r.optString("data").takeIf { it.isNotEmpty() }
            ?: r.optString("video_url").takeIf { it.isNotEmpty() })
            ?.let { downloads.add(DLOption("tt-vid", it, "Video (Tanpa Watermark)", "MP4", true)) }
        r.optJSONObject("music_info")?.optString("url")?.takeIf { it.isNotEmpty() }
            ?.let { downloads.add(DLOption("tt-aud", it, "Audio (MP3)", "MP3", false)) }
        Result.success(VideoResult(
            title = r.optString("title", "Video TikTok"),
            author = r.optJSONObject("author")?.optString("nickname") ?: "Pengguna TikTok",
            thumbnail = r.optString("cover", ""),
            downloads = downloads,
            platform = Platform.TIKTOK
        ))
    } catch (e: Exception) { Result.failure(e) }
}

suspend fun fetchYouTube(url: String, resolution: String): Result<VideoResult> = withContext(Dispatchers.IO) {
    try {
        var mp4: JSONObject? = null
        var mp3: JSONObject? = null
        try {
            val u = "https://api.nexray.eu.cc/downloader/v1/ytmp4?url=${URLEncoder.encode(url, "UTF-8")}&resolusi=$resolution"
            val c = URL(u).openConnection() as HttpURLConnection; c.connectTimeout = 20000; c.readTimeout = 25000
            val j = JSONObject(BufferedReader(InputStreamReader(c.inputStream)).use { it.readText() })
            if (j.optBoolean("status")) mp4 = j.getJSONObject("result")
        } catch (_: Exception) {}
        try {
            val u = "https://api.nexray.eu.cc/downloader/v1/ytmp3?url=${URLEncoder.encode(url, "UTF-8")}"
            val c = URL(u).openConnection() as HttpURLConnection; c.connectTimeout = 20000; c.readTimeout = 25000
            val j = JSONObject(BufferedReader(InputStreamReader(c.inputStream)).use { it.readText() })
            if (j.optBoolean("status")) mp3 = j.getJSONObject("result")
        } catch (_: Exception) {}
        if (mp4 == null && mp3 == null) throw Exception("Semua API gagal")
        val main = mp4 ?: mp3!!
        var thumb = main.optString("thumbnail", "")
        if (thumb.isEmpty()) {
            Regex("(?:youtu\\.be/|[?&]v=)([\\w-]{11})").find(url)?.groupValues?.get(1)?.let {
                thumb = "https://i.ytimg.com/vi/$it/hqdefault.jpg"
            }
        }
        val downloads = mutableListOf<DLOption>()
        mp4?.optString("url")?.takeIf { it.isNotEmpty() }?.let {
            downloads.add(DLOption("yt-vid", it, "Video (MP4)", mp4.optString("quality", "${resolution}p"), true))
        }
        mp3?.optString("url")?.takeIf { it.isNotEmpty() }?.let {
            downloads.add(DLOption("yt-aud", it, "Audio (MP3)", mp3.optString("quality", "320k"), false))
        }
        Result.success(VideoResult(
            title = main.optString("title", "Video YouTube"),
            author = main.optString("author", "Kreator YouTube"),
            thumbnail = thumb,
            downloads = downloads,
            platform = Platform.YOUTUBE
        ))
    } catch (e: Exception) { Result.failure(e) }
}

fun downloadFile(context: Context, url: String, title: String, isVideo: Boolean) {
    val ext = if (isVideo) "mp4" else "mp3"
    val safe = title.take(40).replace(Regex("[^\\w\\-]"), "_")
    val req = DownloadManager.Request(Uri.parse(url))
        .setTitle("SnapDown")
        .setDescription(title)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(
            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC,
            "SnapDown_${safe}_${System.currentTimeMillis()}.$ext"
        )
        .setAllowedOverMetered(true)
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
}

// ─────────────────────────────────────────────
// SHIMMER
// ─────────────────────────────────────────────
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(
        initialValue = -600f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "x"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(NavyCard, Color(0xFF334155), NavyCard),
                    start = Offset(x, 0f), end = Offset(x + 600f, 0f)
                )
            )
    )
}

// ─────────────────────────────────────────────
// RESOLUTION ITEMS
// ─────────────────────────────────────────────
private val RESOLUTIONS = listOf("360" to "360p","480" to "480p","720" to "720p","1080" to "1080p","1440" to "1440p","2160" to "2160p (4K)")

// ─────────────────────────────────────────────
// MAIN APP
// ─────────────────────────────────────────────
@Composable
fun SnapDownApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab    by remember { mutableStateOf(Platform.TIKTOK) }
    var urlInput     by remember { mutableStateOf("") }
    var resolution   by remember { mutableStateOf("1080") }
    var isLoading    by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    var result       by remember { mutableStateOf<VideoResult?>(null) }
    var showResult   by remember { mutableStateOf(false) }
    val cache = remember { mutableMapOf<Platform, VideoResult>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Background radial glow (matching web CSS)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x33375A8C), Color.Transparent),
                        center = Offset(size.width * .5f, 0f),
                        radius = size.width * .9f
                    ),
                    center = Offset(size.width * .5f, 0f),
                    radius = size.width * .9f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x222D1B3D), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * .6f
                    ),
                    center = Offset(size.width, 0f),
                    radius = size.width * .6f
                )
            }
            .background(NavyBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            AppHeader()
            Spacer(Modifier.height(28.dp))
            HeroSection()
            Spacer(Modifier.height(24.dp))
            PlatformTabs(activeTab) { tab ->
                activeTab = tab; urlInput = ""; error = null
                result = cache[tab]; showResult = cache[tab] != null
            }
            Spacer(Modifier.height(16.dp))
            InputCard(
                urlInput = urlInput,
                onUrlChange = { urlInput = it; error = null },
                activeTab = activeTab,
                resolution = resolution,
                onResolutionChange = { resolution = it },
                isLoading = isLoading,
                error = error,
                onPaste = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    urlInput = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                },
                onFetch = {
                    val url = urlInput.trim()
                    if (url.isEmpty()) { error = "URL tidak boleh kosong!"; return@InputCard }
                    if (activeTab == Platform.TIKTOK && !url.contains("tiktok")) { error = "URL TikTok tidak valid!"; return@InputCard }
                    if (activeTab == Platform.YOUTUBE && !url.contains("youtube") && !url.contains("youtu.be")) { error = "URL YouTube tidak valid!"; return@InputCard }
                    scope.launch {
                        isLoading = true; error = null; showResult = true; result = null
                        val res = if (activeTab == Platform.TIKTOK) fetchTikTok(url) else fetchYouTube(url, resolution)
                        isLoading = false
                        res.fold(
                            onSuccess = { result = it; cache[activeTab] = it },
                            onFailure = { showResult = false; error = "Gagal mengambil data. Coba lagi." }
                        )
                    }
                }
            )
            AnimatedVisibility(
                visible = showResult,
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                exit = fadeOut(tween(200))
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    ResultCard(isLoading, result) { option ->
                        result?.let { r ->
                            downloadFile(context, option.url, r.title, option.isVideo)
                            Toast.makeText(context, "⬇ Download dimulai!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            AppFooter()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────
// HEADER
// ─────────────────────────────────────────────
@Composable
fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Brush.linearGradient(listOf(Blue500, Purple500)), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = 18.sp) }
        Spacer(Modifier.width(8.dp))
        Text("Snap", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = SnapWhite)
        Text("Down", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Blue400)
    }
}

// ─────────────────────────────────────────────
// HERO
// ─────────────────────────────────────────────
@Composable
fun HeroSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Live badge
        Row(
            modifier = Modifier
                .background(Color(0x221E293B), RoundedCornerShape(50.dp))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val alpha by pulse.animateFloat(
                initialValue = .4f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "dot"
            )
            Box(Modifier.size(8.dp).background(Blue500.copy(alpha = alpha), CircleShape))
            Text("Server Unduhan Tercepat 2026", fontSize = 12.sp, color = GrayText)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Unduh Video Apapun Dari",
            fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
            color = SnapWhite, textAlign = TextAlign.Center
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "YouTube",
                fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFFF0000), Color(0xFFFF4B4B))))
            )
            Text("&", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = SnapWhite)
            Text(
                "TikTok",
                fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tempel link, pilih format, unduh sekarang.\nTanpa watermark. Gratis.",
            fontSize = 14.sp, color = GrayText,
            textAlign = TextAlign.Center, lineHeight = 22.sp
        )
    }
}

// ─────────────────────────────────────────────
// PLATFORM TABS
// ─────────────────────────────────────────────
@Composable
fun PlatformTabs(activeTab: Platform, onTabChange: (Platform) -> Unit) {
    Row(
        modifier = Modifier
            .background(NavyCard, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PlatformTabBtn("♪ TikTok", Platform.TIKTOK, activeTab, CyanAcc) { onTabChange(Platform.TIKTOK) }
        PlatformTabBtn("▶ YouTube", Platform.YOUTUBE, activeTab, RedYT) { onTabChange(Platform.YOUTUBE) }
    }
}

@Composable
fun PlatformTabBtn(label: String, tab: Platform, active: Platform, accent: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active == tab) NavyBorder else Color.Transparent, tween(200), label = "bg")
    val tc by animateColorAsState(if (active == tab) SnapWhite else GrayText, tween(200), label = "tc")
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active == tab) accent else tc,
            fontWeight = if (active == tab) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

// ─────────────────────────────────────────────
// INPUT CARD
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputCard(
    urlInput: String, onUrlChange: (String) -> Unit,
    activeTab: Platform,
    resolution: String, onResolutionChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onPaste: () -> Unit,
    onFetch: () -> Unit
) {
    val accent = if (activeTab == Platform.TIKTOK) CyanAcc else RedYT
    val placeholder = if (activeTab == Platform.TIKTOK) "Tempel link TikTok di sini..." else "Tempel link YouTube di sini..."
    var dropExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x661E293B), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
    ) {
        // Accent bar top
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(listOf(accent, accent.copy(alpha = .2f))),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        )
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // URL Field
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                placeholder = { Text(placeholder, fontSize = 14.sp, color = GrayText) },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = GrayText, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    TextButton(onClick = onPaste, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Tempel", fontSize = 12.sp, color = GrayText)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = NavyBorder,
                    errorBorderColor = RedYT,
                    focusedTextColor = SnapWhite,
                    unfocusedTextColor = SnapWhite,
                    cursorColor = accent,
                    focusedContainerColor = NavyCard,
                    unfocusedContainerColor = NavyCard,
                    errorContainerColor = NavyCard,
                ),
                shape = RoundedCornerShape(14.dp)
            )

            // Error
            AnimatedVisibility(visible = error != null) {
                error?.let { Text(it, color = RedYT, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)) }
            }

            // Resolution dropdown (YouTube only)
            AnimatedVisibility(visible = activeTab == Platform.YOUTUBE) {
                ExposedDropdownMenuBox(expanded = dropExpanded, onExpandedChange = { dropExpanded = !dropExpanded }) {
                    OutlinedTextField(
                        value = RESOLUTIONS.find { it.first == resolution }?.second ?: "1080p",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Resolusi Video", fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedYT, unfocusedBorderColor = NavyBorder,
                            focusedTextColor = SnapWhite, unfocusedTextColor = SnapWhite,
                            focusedLabelColor = RedYT, unfocusedLabelColor = GrayText,
                            focusedContainerColor = NavyCard, unfocusedContainerColor = NavyCard,
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = dropExpanded,
                        onDismissRequest = { dropExpanded = false },
                        modifier = Modifier.background(NavyCard)
                    ) {
                        RESOLUTIONS.forEach { (v, l) ->
                            DropdownMenuItem(
                                text = { Text(l, color = if (v == resolution) RedYT else SnapWhite, fontSize = 14.sp) },
                                onClick = { onResolutionChange(v); dropExpanded = false },
                                modifier = Modifier.background(if (v == resolution) Color(0x22EF4444) else Color.Transparent)
                            )
                        }
                    }
                }
            }

            // Fetch button (gradient)
            Button(
                onClick = onFetch,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (!isLoading) Brush.horizontalGradient(listOf(Blue600, Indigo600))
                            else Brush.horizontalGradient(listOf(Color(0xFF1E3A5F), Color(0xFF1E2D5F))),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SnapWhite)
                            Text("Memproses...", color = SnapWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ambil Video", color = SnapWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Icon(Icons.Default.ArrowForward, null, tint = SnapWhite, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// RESULT CARD
// ─────────────────────────────────────────────
@Composable
fun ResultCard(isLoading: Boolean, result: VideoResult?, onDownload: (DLOption) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x661E293B), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isLoading || result == null) {
            ShimmerBox(Modifier.fillMaxWidth().height(200.dp))
            ShimmerBox(Modifier.fillMaxWidth(.75f).height(18.dp))
            ShimmerBox(Modifier.fillMaxWidth(.45f).height(14.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(56.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(56.dp))
        } else {
            // Thumbnail
            if (result.thumbnail.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = result.thumbnail,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavyCard),
                    contentScale = ContentScale.FillWidth,
                    loading = { ShimmerBox(Modifier.fillMaxWidth().height(200.dp)) }
                )
            }
            // Platform badge
            val isTT = result.platform == Platform.TIKTOK
            Box(
                modifier = Modifier
                    .background(if (isTT) Color(0x334FACFE) else Color(0x33EF4444), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (isTT) "TikTok" else "YouTube",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isTT) CyanAcc else RedYT
                )
            }
            // Title
            Text(result.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = SnapWhite, maxLines = 3, overflow = TextOverflow.Ellipsis)
            // Author
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Person, null, tint = GrayText, modifier = Modifier.size(14.dp))
                Text("@${result.author}", fontSize = 13.sp, color = GrayText)
            }
            HorizontalDivider(color = NavyBorder)
            // Download options
            result.downloads.forEach { opt -> DLButton(opt) { onDownload(opt) } }
        }
    }
}

// ─────────────────────────────────────────────
// DOWNLOAD BUTTON
// ─────────────────────────────────────────────
@Composable
fun DLButton(option: DLOption, onClick: () -> Unit) {
    val color = if (option.isVideo) Blue500 else Purple500
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyCard, RoundedCornerShape(14.dp))
            .border(1.dp, NavyBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(color.copy(.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (option.isVideo) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                    null, tint = color, modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(option.label, color = SnapWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (option.badge.isNotEmpty()) {
                        Text(
                            option.badge, fontSize = 11.sp, color = color,
                            modifier = Modifier
                                .background(color.copy(.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text("Tap untuk unduh", fontSize = 12.sp, color = GrayText)
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .background(color.copy(.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Download", tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────
// FOOTER
// ─────────────────────────────────────────────
@Composable
fun AppFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x661E293B), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(32.dp).background(Brush.linearGradient(listOf(Blue500, Purple500)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("⚡", fontSize = 14.sp) }
            Text("SnapDown", fontWeight = FontWeight.Bold, color = SnapWhite, fontSize = 16.sp)
        }
        Text("Universal Downloader Tool", fontSize = 12.sp, color = GrayText)
        Text("Dirancang oleh Nexray & Gemini", fontSize = 12.sp, color = GrayText, textAlign = TextAlign.Center)
    }
}
