@file:OptIn(zone.ien.hig.ExperimentalCupertinoApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.samrat.wallet

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import zone.ien.hig.*
import zone.ien.hig.icons.CupertinoIcons
import zone.ien.hig.icons.outlined.*
import zone.ien.hig.theme.CupertinoTheme
import zone.ien.hig.theme.darkColorScheme
import zone.ien.hig.theme.lightColorScheme
import zone.ien.hig.utils.rememberDefaultBackdrop

class MainActivity : ComponentActivity() {
    private val nfcCardReader by lazy { NfcCardReader(this) }
    private var nfcResultHandler: ((NfcScanResult) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalletApp(
                startNfcScan = { handler -> nfcResultHandler = handler; nfcCardReader.start(handler) },
                stopNfcScan = { nfcResultHandler = null; nfcCardReader.stop() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        nfcResultHandler?.let(nfcCardReader::start)
    }

    override fun onPause() {
        nfcCardReader.stop()
        super.onPause()
    }
}

private enum class Page { Home, Search, Add, BankCard, GiftCard, Loyalty, NfcScan, Orders, Notifications }
private enum class PassKind(val color: Long) { Bank(0xFF1C1C1EL), Gift(0xFFD1495BL), Loyalty(0xFF1E5FA8L), Transit(0xFF177A72L), Access(0xFF8A6A3DL) }
private data class Pass(val id: Long, val kind: PassKind, val name: String, val number: String, val image: String = "")

private const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
private val MutedLight = Color(0xFF6B6862)
private val MutedDark = Color(0xFF9A9892)

@Composable
private fun WalletApp(startNfcScan: ((NfcScanResult) -> Unit) -> Unit, stopNfcScan: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wallet", Context.MODE_PRIVATE) }
    val passes = remember { mutableStateListOf<Pass>() }
    var loaded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.Home) }
    var nfcKind by remember { mutableStateOf(PassKind.Transit) }
    val dark = isSystemInDarkTheme()
    val russian = LocalConfiguration.current.locales[0].language == "ru"

    LaunchedEffect(Unit) {
        runCatching {
            val array = JSONArray(prefs.getString("passes", "[]"))
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                passes += Pass(item.getLong("id"), PassKind.valueOf(item.getString("kind")), item.getString("name"), item.optString("number"), item.optString("image"))
            }
        }
        loaded = true
    }
    LaunchedEffect(passes.toList(), loaded) {
        if (loaded) {
            val array = JSONArray()
            passes.forEach { p -> array.put(JSONObject().put("id", p.id).put("kind", p.kind.name).put("name", p.name).put("number", p.number).put("image", p.image)) }
            prefs.edit().putString("passes", array.toString()).apply()
        }
    }

    fun save(pass: Pass) {
        passes.add(0, pass)
        page = Page.Home
        Toast.makeText(context, if (russian) "Карта сохранена" else "Card saved", Toast.LENGTH_SHORT).show()
    }

    BackHandler(page != Page.Home) { if (page == Page.BankCard || page == Page.GiftCard || page == Page.Loyalty || page == Page.NfcScan) page = Page.Add else page = Page.Home }
    CupertinoTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        val backdrop = rememberDefaultBackdrop()
        AnimatedContent(
            targetState = page,
            transitionSpec = { (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "wallet pages",
        ) { target ->
            when (target) {
                Page.Home -> HomePage(passes, dark, russian, backdrop, navigate = { page = it }, pay = { openGoogleWallet(context) }, remove = { pass -> passes.remove(pass); if (pass.image.isNotBlank()) File(pass.image).delete() })
                Page.Search -> SearchPage(passes, dark, russian, backdrop, back = { page = Page.Home }, pay = { openGoogleWallet(context) })
                Page.Add -> AddPage(dark, russian, backdrop, back = { page = Page.Home }, navigate = { page = it }, scanNfc = { nfcKind = it; page = Page.NfcScan }, openWallet = { path -> openGoogleWalletLink(context, path) })
                Page.BankCard -> BankCardForm(dark, russian, backdrop, back = { page = Page.Add }) { id, name, image -> save(Pass(id, PassKind.Bank, name, "Google Wallet", image)) }
                Page.GiftCard -> CardForm(dark, russian, backdrop, PassKind.Gift, back = { page = Page.Add }) { name, number -> save(Pass(System.currentTimeMillis(), PassKind.Gift, name, number)) }
                Page.Loyalty -> CardForm(dark, russian, backdrop, PassKind.Loyalty, back = { page = Page.Add }) { name, number -> save(Pass(System.currentTimeMillis(), PassKind.Loyalty, name, number)) }
                Page.NfcScan -> NfcScanPage(dark, russian, backdrop, startNfcScan, stopNfcScan, back = { stopNfcScan(); page = Page.Add }) { card ->
                    stopNfcScan()
                    val name = when (nfcKind) {
                        PassKind.Transit -> if (russian) "Проездной билет" else "Transit ticket"
                        else -> if (russian) "Пропуск" else "Access card"
                    }
                    save(Pass(System.currentTimeMillis(), nfcKind, name, "${card.type} · ${card.fingerprint.takeLast(8)}"))
                }
                Page.Orders -> OrdersPage(dark, russian, backdrop) { page = Page.Home }
                Page.Notifications -> NotificationsPage(dark, russian, backdrop) { page = Page.Home }
            }
        }
    }
}

@Composable
private fun HomePage(passes: List<Pass>, dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, navigate: (Page) -> Unit, pay: () -> Unit, remove: (Pass) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0E0E10) else Color(0xFFF2F1EE)), contentAlignment = Alignment.TopCenter) {
        // On tablets cards flow into columns; on phones this collapses to a single column.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.widthIn(max = 1280.dp).fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CupertinoText(if (russian) "Кошелёк" else "Wallet", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    CupertinoLiquidIconButton(onClick = { navigate(Page.Add) }, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.Plus, null, Modifier.size(23.dp)) }
                    Spacer(Modifier.width(8.dp))
                    CupertinoLiquidIconButton(onClick = { navigate(Page.Search) }, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.MagnifyingGlass, null, Modifier.size(22.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        CupertinoLiquidIconButton(onClick = { menu = true }, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.Ellipsis, null, Modifier.size(22.dp)) }
                        CupertinoDropdownMenu(expanded = menu, onDismissRequest = { menu = false }, backdrop = backdrop, width = 230.dp) {
                            MenuItem { pad -> MenuContent(if (russian) "Заказы" else "Orders", CupertinoIcons.Default.Cube, pad) { menu = false; navigate(Page.Orders) } }
                            MenuItem { pad -> MenuContent(if (russian) "Уведомления" else "Notifications", CupertinoIcons.Default.Bell, pad) { menu = false; navigate(Page.Notifications) } }
                        }
                    }
                }
            }
            if (passes.isEmpty()) item { EmptyCard(russian, backdrop) { navigate(Page.Add) } }
            items(passes, key = { it.id }) { pass -> PassCard(pass, russian, edit, backdrop, onClick = { if (pass.kind == PassKind.Bank && !edit) pay() }, onLongPress = { edit = true }, remove = { remove(pass) }) }
        }

        AnimatedVisibility(edit, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            CupertinoLiquidButton(onClick = { edit = false }, backdrop = backdrop, modifier = Modifier.navigationBarsPadding().padding(18.dp).widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                CupertinoText(if (russian) "Готово" else "Done", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyCard(russian: Boolean, backdrop: LayerBackdrop, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFC4B8A6)).clickable(onClick = add)) {
        Image(painterResource(R.drawable.wallet_empty), null, Modifier.fillMaxWidth().aspectRatio(1.78f), contentScale = ContentScale.Crop)
        Row(Modifier.fillMaxWidth().background(Color(0xFF3D3A35)).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CupertinoText(if (russian) "Карты и билеты" else "Passes and Tickets", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                CupertinoText(if (russian) "Добавьте первую карту, чтобы собрать всё в одном месте." else "Add your first card to start collecting your passes in one place.", color = Color.White.copy(.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(12.dp))
            CupertinoLiquidButton(onClick = add, backdrop = backdrop, shape = CircleShape) { CupertinoText(if (russian) "Добавить" else "Add", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PassCard(pass: Pass, russian: Boolean, edit: Boolean, backdrop: LayerBackdrop, onClick: () -> Unit, onLongPress: () -> Unit, remove: () -> Unit) {
    val photo = rememberCardPhoto(pass.image)
    Box(Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(20.dp)).background(Color(pass.kind.color)).combinedClickable(onClick = onClick, onLongClick = onLongPress)) {
        if (photo != null) Image(photo, pass.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CupertinoText(kindTitle(pass.kind, russian).uppercase(), color = Color.White.copy(.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp).background(Color.White.copy(.22f), CircleShape), contentAlignment = Alignment.Center) { CupertinoIcon(kindIcon(pass.kind), null, Modifier.size(27.dp), tint = Color.White) }
            }
            Spacer(Modifier.weight(1f))
            CupertinoText(pass.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pass.number.isNotBlank()) CupertinoText(pass.number, color = Color.White.copy(.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(edit, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp), enter = scaleIn(), exit = scaleOut()) {
            CupertinoLiquidIconButton(onClick = remove, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.Xmark, null, Modifier.size(16.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun SearchPage(passes: List<Pass>, dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, back: () -> Unit, pay: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = passes.filter { it.name.contains(query, true) || it.number.contains(query, true) || kindTitle(it.kind, russian).contains(query, true) }
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0E0E10) else Color(0xFFF2F1EE)), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.widthIn(max = 1280.dp).fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CupertinoTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f), leadingIcon = { CupertinoIcon(CupertinoIcons.Default.MagnifyingGlass, null, Modifier.size(19.dp)) }, placeholder = { CupertinoText(if (russian) "Поиск в Кошельке" else "Search in Wallet") })
                    Spacer(Modifier.width(10.dp))
                    CupertinoLiquidIconButton(onClick = back, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.Xmark, null, Modifier.size(20.dp)) }
                }
            }
            if (query.isNotBlank() && matches.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                CupertinoText(if (russian) "Ничего не найдено" else "No cards match your search", color = MutedDark, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 52.dp))
            }
            items(if (query.isBlank()) passes else matches, key = { it.id }) { pass ->
                PassCard(pass, russian, false, backdrop, onClick = { if (pass.kind == PassKind.Bank) pay() }, onLongPress = {}, remove = {})
            }
        }
    }
}

@Composable
private fun AddPage(dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, back: () -> Unit, navigate: (Page) -> Unit, scanNfc: (PassKind) -> Unit, openWallet: (String) -> Unit) = PageScaffold(dark, if (russian) "Добавить в Кошелёк" else "Add to Wallet", backdrop, back) {
    CupertinoText(if (russian) "Храните все карты, ключи и билеты, которыми пользуетесь каждый день, в одном месте." else "Keep all the cards, keys and passes you use every day all in one place.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp))
    AddRow(if (russian) "Карта из Google Wallet" else "Card from Google Wallet", if (russian) "Фото карты, оплата через Google Wallet" else "Card photo, pay with Google Wallet", backdrop, icon = { GoogleWalletIcon(40.dp) }) { navigate(Page.BankCard) }
    AddRow(if (russian) "Новая банковская карта" else "New payment card", if (russian) "Добавить в Google Wallet" else "Add to Google Wallet", backdrop, icon = { BlueIcon(CupertinoIcons.Default.Creditcard) }) { openWallet("addfop") }
    AddRow(kindTitle(PassKind.Gift, russian), if (russian) "Магазин и номер карты" else "Store and card number", backdrop, icon = { KindIcon(PassKind.Gift) }) { navigate(Page.GiftCard) }
    AddRow(kindTitle(PassKind.Loyalty, russian), if (russian) "Программа и номер участника" else "Program and member number", backdrop, icon = { KindIcon(PassKind.Loyalty) }) { navigate(Page.Loyalty) }
    AddRow(kindTitle(PassKind.Transit, russian), if (russian) "Сканировать NFC-карту" else "Scan NFC card", backdrop, icon = { KindIcon(PassKind.Transit) }) { scanNfc(PassKind.Transit) }
    AddRow(kindTitle(PassKind.Access, russian), if (russian) "Сканировать NFC-карту" else "Scan NFC card", backdrop, icon = { KindIcon(PassKind.Access) }) { scanNfc(PassKind.Access) }
    AddRow(if (russian) "Другое" else "Other", if (russian) "Создать в Google Wallet" else "Create in Google Wallet", backdrop, icon = { BlueIcon(CupertinoIcons.Default.Ellipsis, Color(0xFF8A8F98)) }) { openWallet("additem") }
}

@Composable
private fun BankCardForm(dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, back: () -> Unit, save: (Long, String, String) -> Unit) {
    val context = LocalContext.current
    val id = remember { System.currentTimeMillis() }
    var name by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    val photo = rememberCardPhoto(image)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            val file = File(context.filesDir, "cards/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            if (image.isNotBlank()) File(image).delete()
            image = file.absolutePath
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    FormPage(dark, if (russian) "Карта из Google Wallet" else "Card from Google Wallet", backdrop, back, saveEnabled = name.isNotBlank() && image.isNotBlank(), save = { save(id, name.trim(), image) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(20.dp)).background(if (dark) Color(0xFF252527) else Color.White).clickable(onClick = pick),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) Image(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CupertinoIcon(CupertinoIcons.Default.Creditcard, null, Modifier.size(44.dp), tint = Color(0xFF3478F6))
                CupertinoText(if (russian) "Выбрать фото карты" else "Choose card photo", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Field(if (russian) "Название" else "Name", name, { name = it.take(40) }, "Visa •• 4443")
        CupertinoText(
            if (russian) "Нажатие на карту откроет Google Wallet — приложите телефон к терминалу, чтобы оплатить." else "Tapping the card opens Google Wallet — hold your phone to the terminal to pay.",
            color = if (dark) MutedDark else MutedLight, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun rememberCardPhoto(path: String) = remember(path) {
    if (path.isBlank()) return@remember null
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun CardForm(dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, kind: PassKind, back: () -> Unit, save: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val gift = kind == PassKind.Gift
    FormPage(dark, kindTitle(kind, russian), backdrop, back, saveEnabled = name.isNotBlank(), save = { save(name.trim(), number.trim()) }) {
        Box(Modifier.fillMaxWidth().height(190.dp).background(Color(kind.color), RoundedCornerShape(22.dp)).padding(20.dp)) {
            CupertinoText(kindTitle(kind, russian).uppercase(), color = Color.White.copy(.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.align(Alignment.BottomStart)) {
                CupertinoText(name.ifBlank { if (gift) (if (russian) "Магазин" else "Store") else (if (russian) "Программа" else "Program") }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                CupertinoText(number.ifBlank { "••••" }, color = Color.White.copy(.85f), fontSize = 13.sp)
            }
        }
        Field(if (gift) (if (russian) "Магазин" else "Store") else (if (russian) "Программа лояльности" else "Loyalty program"), name, { name = it.take(60) }, if (gift) "IKEA" else "Starbucks Rewards")
        Field(if (gift) (if (russian) "Номер карты" else "Card number") else (if (russian) "Номер участника" else "Member number"), number, { number = it.take(40) }, "1234 5678", KeyboardType.Number)
    }
}

@Composable
private fun NfcScanPage(dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, startScan: ((NfcScanResult) -> Unit) -> Unit, stopScan: () -> Unit, back: () -> Unit, save: (NfcCardSnapshot) -> Unit) {
    var result by remember { mutableStateOf<NfcScanResult?>(null) }
    val beginScan = {
        result = null
        startScan { scanned ->
            if (scanned.card != null) stopScan()
            result = scanned
        }
    }

    DisposableEffect(Unit) {
        beginScan()
        onDispose(stopScan)
    }

    PageScaffold(dark, if (russian) "Сканирование NFC" else "Scan NFC card", backdrop, back) {
        Column(Modifier.fillMaxWidth().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp).background(Color(0xFF0A84FF).copy(.14f), CircleShape), contentAlignment = Alignment.Center) {
                CupertinoIcon(if (result?.card != null) CupertinoIcons.Default.Checkmark else CupertinoIcons.Default.Key, null, Modifier.size(54.dp), tint = if (result?.card != null) Color(0xFF34C759) else Color(0xFF0A84FF))
            }
            CupertinoText(
                when {
                    result?.card != null -> if (russian) "Карта считана" else "Card detected"
                    result?.error != null -> if (russian) "Сканирование недоступно" else "Scanning unavailable"
                    else -> if (russian) "Приложите карту к задней части телефона" else "Hold the card near the back of your phone"
                },
                fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp),
            )
        }

        result?.error?.let { error ->
            CupertinoText(error, color = Color(0xFFFF453A), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 34.dp))
            CupertinoLiquidButton(onClick = beginScan, backdrop = backdrop, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(18.dp)) {
                CupertinoText(if (russian) "Повторить" else "Try again", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }

        result?.card?.let { card ->
            CupertinoText("${card.type} · ${card.fingerprint.takeLast(8)}", color = if (dark) MutedDark else MutedLight, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            CupertinoLiquidButton(onClick = { save(card) }, backdrop = backdrop, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(18.dp)) {
                CupertinoText(if (russian) "Сохранить карту" else "Save card", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrdersPage(dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, back: () -> Unit) = PageScaffold(dark, if (russian) "Заказы" else "Orders", backdrop, back) {
    Column(Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) { CupertinoIcon(CupertinoIcons.Default.Cube, null, Modifier.size(56.dp)); CupertinoText(if (russian) "Нет заказов" else "No Orders", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)); CupertinoText(if (russian) "Заказы у партнёров-продавцов будут отображаться здесь." else "Orders you place with participating merchants will appear here.", textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp, 8.dp)) }
}

@Composable
private fun NotificationsPage(dark: Boolean, russian: Boolean, backdrop: LayerBackdrop, back: () -> Unit) = PageScaffold(dark, if (russian) "Уведомления" else "Notifications", backdrop, back) {
    Toggle(if (russian) "Заказы" else "Orders"); Toggle(if (russian) "Предавторизованные платежи" else "Preauthorized Payments"); CupertinoText(if (russian) "Получайте уведомления о предстоящих платежах." else "Receive notifications related to upcoming payments.", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)); Toggle(if (russian) "Новые функции и обновления" else "New Features & Updates"); Toggle(if (russian) "Акции и предложения" else "Offers & Promotions")
}

@Composable private fun PageShell(dark: Boolean, content: @Composable BoxScope.() -> Unit) { Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0E0E10) else Color(0xFFF2F1EE)).statusBarsPadding().navigationBarsPadding(), content = content) }

@Composable
private fun PageScaffold(dark: Boolean, title: String, backdrop: LayerBackdrop, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    CenteredPage(dark, title, backdrop, back, trailing = { Spacer(Modifier.size(48.dp)) }, content = content)

@Composable
private fun FormPage(dark: Boolean, title: String, backdrop: LayerBackdrop, back: () -> Unit, saveEnabled: Boolean, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    CenteredPage(dark, title, backdrop, back, trailing = { CupertinoLiquidIconButton(onClick = save, enabled = saveEnabled, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.Checkmark, null, Modifier.size(20.dp)) } }, content = content)

/** Secondary pages keep a readable column width on tablets instead of stretching edge to edge. */
@Composable
private fun CenteredPage(dark: Boolean, title: String, backdrop: LayerBackdrop, back: () -> Unit, trailing: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) = PageShell(dark) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CupertinoLiquidIconButton(onClick = back, backdrop = backdrop) { CupertinoIcon(CupertinoIcons.Default.Xmark, null, Modifier.size(20.dp)) }
            CupertinoText(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            trailing()
        }
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxWidth(), contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp)) {
            item { Column(Modifier.fillMaxWidth(), content = content) }
        }
    }
}

@Composable private fun Field(label: String, value: String, change: (String) -> Unit, placeholder: String, type: KeyboardType = KeyboardType.Text) { CupertinoText(label.uppercase(), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)); CupertinoTextField(value, change, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), placeholder = { CupertinoText(placeholder) }) }

@Composable
private fun AddRow(title: String, sub: String, backdrop: LayerBackdrop, icon: @Composable () -> Unit, click: () -> Unit) {
    CupertinoLiquidButton(onClick = click, backdrop = backdrop, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                CupertinoText(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) CupertinoText(sub, fontSize = 13.sp, color = MutedDark, textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CupertinoIcon(CupertinoIcons.Default.ChevronForward, null, Modifier.size(15.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable private fun KindIcon(kind: PassKind) = BlueIcon(kindIcon(kind), Color(kind.color))

@Composable private fun BlueIcon(icon: ImageVector, color: Color = Color(0xFF3478F6)) { Box(Modifier.size(40.dp).background(color, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { CupertinoIcon(icon, null, Modifier.size(22.dp), tint = Color.White) } }

/** The icon of the installed Google Wallet app; the launcher icon of this app is not reused here. */
@Composable
private fun GoogleWalletIcon(size: Dp) {
    val context = LocalContext.current
    val icon = remember {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(GOOGLE_WALLET_PACKAGE)
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 192, 192)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        }.getOrNull()
    }
    if (icon != null) Image(icon, "Google Wallet", Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)))
    else Box(Modifier.size(size).background(Color(0xFF3478F6), RoundedCornerShape(size * 0.24f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoIcons.Default.Creditcard, null, Modifier.size(size * 0.55f), tint = Color.White) }
}

@Composable private fun Toggle(title: String) { var enabled by remember { mutableStateOf(true) }; Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { CupertinoText(title, fontSize = 16.sp, modifier = Modifier.weight(1f)); CupertinoSwitch(enabled, { enabled = it }) } }

@Composable private fun MenuContent(title: String, icon: ImageVector, padding: PaddingValues, click: () -> Unit) { CupertinoButton(onClick = click, modifier = Modifier.fillMaxWidth(), colors = CupertinoButtonDefaults.plainButtonColors(), contentPadding = padding) { CupertinoIcon(icon, null, Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)); CupertinoText(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Start) } }

private fun kindTitle(kind: PassKind, russian: Boolean) = when (kind) {
    PassKind.Bank -> if (russian) "Банковская карта" else "Payment card"
    PassKind.Gift -> if (russian) "Подарочная карта" else "Gift card"
    PassKind.Loyalty -> if (russian) "Карта постоянного клиента" else "Loyalty card"
    PassKind.Transit -> if (russian) "Проездной билет" else "Transit ticket"
    PassKind.Access -> if (russian) "Пропуск или ключ" else "Access card or key"
}

private fun kindIcon(kind: PassKind): ImageVector = when (kind) {
    PassKind.Bank -> CupertinoIcons.Default.Creditcard
    PassKind.Gift -> CupertinoIcons.Default.Gift
    PassKind.Loyalty -> CupertinoIcons.Default.WalletPass
    PassKind.Transit -> CupertinoIcons.Default.Car
    PassKind.Access -> CupertinoIcons.Default.Key
}

private fun openGoogleWalletLink(context: Context, path: String) {
    val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://wallet.google.com/gw/app/$path")).setPackage(GOOGLE_WALLET_PACKAGE)
    runCatching { context.startActivity(link) }.onFailure { openGoogleWalletInStore(context) }
}

private fun openGoogleWallet(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(GOOGLE_WALLET_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (launchIntent != null) context.startActivity(launchIntent) else openGoogleWalletInStore(context)
}

private fun openGoogleWalletInStore(context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_WALLET_PACKAGE")))
}
