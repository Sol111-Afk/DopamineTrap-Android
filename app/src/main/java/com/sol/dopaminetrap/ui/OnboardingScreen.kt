package com.sol.dopaminetrap.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sol.dopaminetrap.R
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sol.dopaminetrap.FirebaseRepository
import com.sol.dopaminetrap.OnboardingManager
import com.sol.dopaminetrap.OnboardingManager.DeviceMode
import com.sol.dopaminetrap.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.roundToInt

private val GradientBg = Modifier.background(Brush.verticalGradient(listOf(SfCream, SfBg2)))

private fun generatePairingCode(): String =
    (100000..999999).random().toString()

// ── Pasul 1 — alegere mod ────────────────────────────────────────────────────

@Composable
fun ModeSelectionScreen(onModeSelected: (DeviceMode) -> Unit) {
    val teal = Color(0xFF3EDCB5)
    val blue = Color(0xFF5B8FE0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F7FA), Color(0xFFE8EFF8))))
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(2f))

        Image(
            painter            = painterResource(R.drawable.ic_safeland_logo),
            contentDescription = null,
            modifier           = Modifier.size(220.dp).align(Alignment.CenterHorizontally).offset(x = (-10).dp),
            contentScale       = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SfDark, fontWeight = FontWeight.Bold)) { append("safe ") }
                withStyle(SpanStyle(color = teal,   fontWeight = FontWeight.Bold)) { append("land") }
            },
            fontSize      = 40.sp,
            letterSpacing = 0.sp
        )

        Spacer(Modifier.weight(0.5f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.horizontalGradient(listOf(teal, blue)))
                .clickable { onModeSelected(DeviceMode.PARENT) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Părinte",
                color      = Color.White,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick  = { onModeSelected(DeviceMode.CHILD) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(28.dp),
            border   = BorderStroke(1.5.dp, Color(0xFFD0DCE8)),
            colors   = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor   = SfDark
            )
        ) {
            Text("Copil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SfDark)) { append("DISCONNECT ") }
                withStyle(SpanStyle(color = teal))   { append("TODAY.") }
                withStyle(SpanStyle(color = SfDark)) { append(" CONNECT ") }
                withStyle(SpanStyle(color = teal))   { append("TOMORROW.") }
            },
            fontSize      = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight    = FontWeight.Medium,
            textAlign     = TextAlign.Center,
            maxLines      = 1
        )

        Spacer(Modifier.weight(2f))
    }
}

// ── Pasul 2a — copil: nume + vârstă, generează cod ──────────────────────────

@Composable
fun ChildSetupScreen(
    onDone: (familyId: String, childId: String, childName: String, childAge: Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var childName    by remember { mutableStateOf("") }
    var childAge     by remember { mutableStateOf(13) }
    var nameConfirmed by remember { mutableStateOf(false) }

    var pairingCode by remember { mutableStateOf("") }
    var familyId    by remember { mutableStateOf("") }
    var childId     by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf<String?>(null) }

    fun generateCode(name: String) {
        isLoading = true
        error = null
        scope.launch {
            runCatching {
                val fid  = UUID.randomUUID().toString()
                val cid  = UUID.randomUUID().toString()
                val code = generatePairingCode()
                Firebase.firestore.collection("pairing").document(code).set(
                    mapOf(
                        "familyId"  to fid,
                        "childId"   to cid,
                        "childName" to name,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
                FirebaseRepository.registerChild(fid, cid, name)
                familyId    = fid
                childId     = cid
                pairingCode = code
            }.onFailure { error = "Eroare la generarea codului. Verifica conexiunea la internet." }
            isLoading = false
        }
    }

    val teal = Color(0xFF3EDCB5)
    val blue = Color(0xFF5B8FE0)

    Column(modifier = Modifier.fillMaxSize().imePadding().background(Color(0xFFF5F7FA))) {

        // ── Header gradient ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(teal, blue)))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp)
        ) {
            Column {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Înapoi", tint = Color.White)
                }
                Text(
                    "Configurează pentru copil",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = SfDark
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cum se numește copilul și câți ani are?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SfGray
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Conținut ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            if (!nameConfirmed) {
                Text(
                    "PRENUMELE COPILULUI",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = SfLGray,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(12.dp),
                    color           = Color.White,
                    shadowElevation = 2.dp
                ) {
                    TextField(
                        value         = childName,
                        onValueChange = { childName = it },
                        placeholder   = { Text("ex: Ionel", color = SfLGray) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = TextFieldDefaults.colors(
                            unfocusedContainerColor  = Color.White,
                            focusedContainerColor    = Color.White,
                            unfocusedIndicatorColor  = Color.Transparent,
                            focusedIndicatorColor    = Color.Transparent,
                            unfocusedTextColor       = SfDark,
                            focusedTextColor         = SfDark
                        )
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("VÂRSTA", style = MaterialTheme.typography.labelSmall, color = SfLGray, letterSpacing = 1.5.sp)
                    Text("$childAge ani", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = blue)
                }
                Slider(
                    value         = childAge.toFloat(),
                    onValueChange = { childAge = it.roundToInt() },
                    valueRange    = 3f..18f,
                    steps         = 14,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = SliderDefaults.colors(
                        thumbColor        = blue,
                        activeTrackColor  = teal,
                        inactiveTrackColor = SfBorder
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("3 ani",  style = MaterialTheme.typography.labelSmall, color = SfLGray)
                    Text("18 ani", style = MaterialTheme.typography.labelSmall, color = SfLGray)
                }
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.horizontalGradient(listOf(teal, blue)))
                        .clickable {
                            nameConfirmed = true
                            generateCode(childName.trim().ifEmpty { "Copil" })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Continuă", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

            } else {
                Text(
                    "Dă codul de mai jos părintelui. El îl va introduce în aplicația lui pentru a se conecta.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = SfGray
                )
                Spacer(Modifier.height(40.dp))
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = teal)
                    }
                } else if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.horizontalGradient(listOf(teal, blue)))
                            .clickable { generateCode(childName.trim().ifEmpty { "Copil" }) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Încearcă din nou", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        border    = BorderStroke(1.dp, SfBorder),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Text(
                            pairingCode,
                            modifier   = Modifier.padding(24.dp).fillMaxWidth(),
                            style      = MaterialTheme.typography.displayMedium.copy(
                                fontFamily    = FontFamily.Monospace,
                                letterSpacing = 8.sp
                            ),
                            textAlign  = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color      = SfDark
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.horizontalGradient(listOf(teal, blue)))
                            .clickable {
                                isLoading = true
                                scope.launch {
                                    val resolvedFamilyId = runCatching {
                                        Firebase.firestore.collection("pairing").document(pairingCode)
                                            .get().await().getString("resolvedFamilyId")
                                    }.getOrNull() ?: familyId
                                    if (resolvedFamilyId != familyId) {
                                        runCatching {
                                            FirebaseRepository.registerChild(
                                                resolvedFamilyId, childId,
                                                childName.trim().ifEmpty { "Copil" }
                                            )
                                        }
                                    }
                                    isLoading = false
                                    onDone(resolvedFamilyId, childId, childName.trim().ifEmpty { "Copil" }, childAge)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading)
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else
                            Text("Părintele a introdus codul — Continuă", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Pasul 2b — parinte: introduce codul copilului ────────────────────────────

@Composable
fun ParentSetupScreen(
    onDone: (familyId: String, childId: String, childName: String) -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var code      by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().then(GradientBg)) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier.padding(top = 36.dp, start = 8.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Înapoi", tint = SfDark)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Conecteaza-te la copil",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = SfDark
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Introdu codul de 6 cifre afisat pe telefonul copilului.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = SfGray
            )
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value         = code,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                label         = { Text("Cod de 6 cifre") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                textStyle     = LocalTextStyle.current.copy(
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 28.sp,
                    letterSpacing = 6.sp,
                    textAlign     = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = SfDark,
                    unfocusedBorderColor = SfBorder,
                    focusedLabelColor    = SfDark,
                    unfocusedLabelColor  = SfGray,
                    focusedTextColor     = SfDark,
                    unfocusedTextColor   = SfDark
                )
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))

            val canConnect = !isLoading && code.length == 6
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (canConnect)
                            Brush.horizontalGradient(listOf(Color(0xFF3EDCB5), Color(0xFF5B8FE0)))
                        else
                            Brush.horizontalGradient(listOf(Color(0xFFB0BEC5), Color(0xFFB0BEC5)))
                    )
                    .clickable(enabled = canConnect) {
                        if (code.length != 6) { error = "Codul trebuie sa aiba 6 cifre."; return@clickable }
                        isLoading = true
                        error = null
                        scope.launch {
                            runCatching {
                                val doc  = Firebase.firestore.collection("pairing").document(code).get().await()
                                if (!doc.exists()) throw Exception("Cod invalid sau expirat.")
                                val fid  = doc.getString("familyId")  ?: throw Exception("Date corupte.")
                                val cid  = doc.getString("childId")   ?: throw Exception("Date corupte.")
                                val name = doc.getString("childName") ?: "Copil"
                                onDone(fid, cid, name)
                            }.onFailure { error = it.message ?: "Eroare necunoscuta."; isLoading = false }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Conectează", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Pasul 3 (doar copil) — ghid permisiuni ───────────────────────────────────

@Composable
fun PermissionsGuideScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    var notifGranted    by remember { mutableStateOf(false) }
    var a11yEnabled     by remember { mutableStateOf(false) }
    var notifListenerOn by remember { mutableStateOf(false) }
    var smsGranted      by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        val a11yFlat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        a11yEnabled = a11yFlat.contains(context.packageName, ignoreCase = true)
        val nlFlat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        notifListenerOn = nlFlat.contains(context.packageName)
        smsGranted = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }
    val smsLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshPermissions() }

    LaunchedEffect(Unit) { refreshPermissions() }

    val permissions = listOf(
        Triple("Notificări",                 "Necesar pentru alerte despre conținut periculos.",                        notifGranted),
        Triple("Serviciu de accesibilitate", "Detectează conținut YouTube Shorts și TikTok pentru throttle.",           a11yEnabled),
        Triple("Acces la notificări",        "Monitorizează mesajele din WhatsApp, Telegram și alte app-uri.",          notifListenerOn),
        Triple("Permisiune SMS",             "Monitorizează mesajele SMS primite.",                                     smsGranted)
    )

    val teal = Color(0xFF3EDCB5)
    val blue = Color(0xFF5B8FE0)

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {

        // ── Header gradient ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.horizontalGradient(listOf(teal, blue)))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 72.dp)) {
                Text(
                    "Activează permisiunile",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Aceste permisiuni permit Safeland să funcționeze corect. Le poți activa oricând din setările telefonului.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠️", fontSize = 26.sp)
            }
        }

        // ── Conținut ─────────────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            permissions.forEachIndexed { index, (title, desc, granted) ->
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (granted) teal else Color.Transparent)
                                .then(if (!granted) Modifier else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (granted) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .then(Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Color.White)
                                    )
                                }
                                Text("□", color = blue, fontSize = 26.sp)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = SfDark)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = SfGray)
                        }
                        if (!granted) {
                            TextButton(
                                onClick = {
                                    when (index) {
                                        0 -> { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                        1 -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        2 -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        3 -> smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                                    }
                                }
                            ) {
                                Text("Activează", style = MaterialTheme.typography.labelMedium,
                                    color = blue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(teal, blue)))
                    .clickable { onComplete() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (permissions.all { it.third }) "Gata — intru în aplicație"
                    else "Continuă fără toate permisiunile",
                    color      = Color.White,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!permissions.all { it.third }) {
                TextButton(
                    onClick  = { refreshPermissions() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Actualizează starea permisiunilor",
                        style = MaterialTheme.typography.bodySmall, color = SfLGray)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Orchestrator ──────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var selectedMode    by remember { mutableStateOf<DeviceMode?>(null) }
    var childSetupDone  by remember { mutableStateOf(false) }
    var pendingFamilyId by remember { mutableStateOf("") }
    var pendingChildId  by remember { mutableStateOf("") }
    var pendingName     by remember { mutableStateOf("") }
    var pendingAge      by remember { mutableStateOf(13) }

    when {
        selectedMode == null ->
            ModeSelectionScreen(onModeSelected = { selectedMode = it })

        selectedMode == DeviceMode.CHILD && !childSetupDone ->
            ChildSetupScreen(
                onDone = { fid, cid, name, age ->
                    pendingFamilyId = fid; pendingChildId = cid; pendingName = name; pendingAge = age
                    childSetupDone  = true
                },
                onBack = { selectedMode = null }
            )

        selectedMode == DeviceMode.CHILD && childSetupDone ->
            PermissionsGuideScreen(onComplete = {
                scope.launch {
                    runCatching {
                        val initialSettings = com.sol.dopaminetrap.data.FamilySettings(childAge = pendingAge)
                        FirebaseRepository.pushSettings(pendingFamilyId, pendingChildId, initialSettings)
                    }
                    OnboardingManager.completeOnboarding(
                        context, DeviceMode.CHILD,
                        pendingFamilyId, pendingChildId, pendingName, pendingAge
                    )
                    onOnboardingComplete()
                }
            })

        selectedMode == DeviceMode.PARENT ->
            ParentSetupScreen(
                onDone = { fid, cid, name ->
                    OnboardingManager.completeOnboarding(context, DeviceMode.PARENT, fid, cid, name)
                    onOnboardingComplete()
                },
                onBack = { selectedMode = null }
            )
    }
}
