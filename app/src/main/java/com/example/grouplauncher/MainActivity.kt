package com.example.grouplauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap)

sealed class Dlg {
    object NewGroup : Dlg()
    data class NewSub(val parent: String) : Dlg()
    data class Rename(val id: String, val name: String) : Dlg()
    data class Settings(val id: String) : Dlg()
    data class Pick(val id: String) : Dlg()
    data class AddTo(val pkgs: List<String>) : Dlg()
}

/** Estado de selección múltiple (tarjeta "Sin grupo"). */
class Sel(val on: Boolean, val set: Set<String>, val toggle: (String) -> Unit, val start: (String) -> Unit)

fun List<String>.moved(p: String, d: Int): List<String> {
    val i = indexOf(p); val j = i + d
    if (i < 0 || j !in indices) return this
    return toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
}

/** Tipografías: se descargan de Google Fonts al compilar (assets/fonts). Si falta alguna, usa la normal. */
object Fonts {
    val names = listOf("Normal", "Pacifico", "Lobster", "Bebas Neue", "Righteous", "Abril Fatface", "Satisfy", "Marker", "Monoton", "Dancing", "Playfair", "Serif")
    private val files = listOf(null, "pacifico", "lobster", "bebasneue", "righteous", "abrilfatface", "satisfy", "permanentmarker", "monoton", "dancingscript", "playfair", null)
    private val cache = HashMap<Int, FontFamily>()
    fun get(ctx: Context, i: Int): FontFamily = cache.getOrPut(i) {
        val f = files.getOrNull(i)
        when {
            i == 11 -> FontFamily.Serif
            f != null && ctx.assets.list("fonts")?.contains("$f.ttf") == true -> FontFamily(Font("fonts/$f.ttf", ctx.assets))
            else -> FontFamily.Default
        }
    }
}

val Palette = listOf<Long>(0x15171C, 0x1E1B4B, 0x4A1D6B, 0x831843, 0x7F1D1D, 0x78350F, 0x1B4332, 0x134E4A, 0x0C4A6E, 0x374151)

fun openHomeSettings(ctx: Context) {
    try { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private lateinit var state: LauncherState
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8AB4F8))) {
                var apps by remember { mutableStateOf(emptyMap<String, AppInfo>()) }
                LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps() } }
                App(state, apps)
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); state.drawerOpen = false } // botón Home
    override fun onResume() { super.onResume(); tick++ }

    private fun loadApps(): Map<String, AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .associate {
                val p = it.activityInfo.packageName
                p to AppInfo(p, it.loadLabel(packageManager).toString(), it.loadIcon(packageManager).toBitmap(144, 144).asImageBitmap())
            }
    }
}

@Composable
fun App(s: LauncherState, apps: Map<String, AppInfo>) {
    val showDrawer = s.drawerOpen || !s.homeEnabled
    BackHandler(showDrawer && s.homeEnabled) { s.drawerOpen = false }
    Box(Modifier.fillMaxSize()) {
        if (s.homeEnabled) HomeScreen(s, apps)
        AnimatedVisibility(showDrawer, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Box(Modifier.fillMaxSize().background(if (s.homeEnabled) Color(0xF2101018) else Color.Transparent)) { Drawer(s, apps) }
        }
    }
}

// ───────────── HOME ─────────────
@Composable
fun HomeScreen(s: LauncherState, apps: Map<String, AppInfo>) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    val pages = s.home
    val pager = rememberPagerState { pages.size }
    Box(Modifier.fillMaxSize().background(Color(0x44000000)).pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total < -100f) s.drawerOpen = true },
            onVerticalDrag = { _, dy -> total += dy },
        )
    }) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val pg = pages[i]
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(48.dp))
                    Text(pg.name, Modifier.weight(1f), color = Color.White, fontSize = 30.sp, textAlign = TextAlign.Center,
                        fontFamily = Fonts.get(ctx, pg.font), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ s.edit = !s.edit }) { Icon(if (s.edit) Icons.Default.Check else Icons.Default.Edit, "Editar", tint = Color.White) }
                }
                if (s.edit) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton({ dlg = Dlg.Pick(pg.id) }) { Text("+ Apps") }
                    TextButton({ dlg = Dlg.Rename(pg.id, pg.name) }) { Text("Título") }
                    TextButton({ dlg = Dlg.Settings(pg.id) }) { Text("Ajustes") }
                    TextButton({ s.saveHome(s.home + Group(name = "Página ${s.home.size + 1}", rows = 5, size = 56)) }) { Text("+ Página") }
                    if (pages.size > 1) TextButton({ s.saveHome(s.home - pg) }) { Text("Borrar") }
                }
                Box(Modifier.weight(1f).padding(top = 8.dp)) { HomeGrid(pg, s, apps) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (pages.size > 1) Row {
                        repeat(pages.size) {
                            Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape)
                                .background(if (it == pager.currentPage) Color.White else Color.White.copy(.3f)))
                        }
                    }
                    Text("⌃ Desliza hacia arriba para abrir el cajón", color = Color.White.copy(.5f), fontSize = 11.sp)
                }
            }
        }
    }
    DialogHost(dlg, { dlg = null }, s, apps)
}

@Composable
fun HomeGrid(pg: Group, s: LauncherState, apps: Map<String, AppInfo>) {
    val list = pg.apps.mapNotNull { apps[it] }.take(pg.cols * pg.rows)
    Column(Modifier.fillMaxSize()) {
        repeat(pg.rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(pg.cols) { c ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        list.getOrNull(r * pg.cols + c)?.let { a ->
                            Cell(a, pg.size, { p -> s.upd(pg.id) { it.copy(apps = it.apps - p) } }, null,
                                { p, d -> s.upd(pg.id) { it.copy(apps = it.apps.moved(p, d)) } }, null)
                        }
                    }
                }
            }
        }
    }
}

// ───────────── CAJÓN DE APPS ─────────────
@Composable
fun Drawer(s: LauncherState, apps: Map<String, AppInfo>) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    val selObj = Sel(selecting, sel, { p -> sel = if (p in sel) sel - p else sel + p }, { p -> selecting = true; sel = setOf(p) })
    val shown = if (query.isBlank()) s.groups else s.groups.filter { g ->
        g.name.contains(query, true) || g.subs.any { it.name.contains(query, true) }
    }
    val grouped = s.groups.flatMap { it.apps + it.subs.flatMap { sg -> sg.apps } }.toSet()
    val ungrouped = apps.values.filter { it.pkg !in grouped && (query.isBlank() || it.label.contains(query, true)) }
        .sortedBy { it.label.lowercase() }
    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (s.homeEnabled) IconButton({ s.drawerOpen = false }) { Icon(Icons.Default.KeyboardArrowDown, "Inicio", tint = Color.White) }
                    Text("Mis grupos", fontSize = 26.sp, color = Color.White, modifier = Modifier.weight(1f))
                    if (s.edit) FilledTonalIconButton({ dlg = Dlg.NewGroup }) { Icon(Icons.Default.Add, "Nuevo grupo") }
                    IconButton({ s.edit = !s.edit }) { Icon(if (s.edit) Icons.Default.Check else Icons.Default.Edit, "Editar", tint = Color.White) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Menú", tint = Color.White) }
                        DropdownMenu(menu, { menu = false }) {
                            val notifOn = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
                            if (!notifOn) DropdownMenuItem({ Text("Activar insignias de notificaciones") }, {
                                menu = false; ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            })
                            DropdownMenuItem({ Text(if (s.homeEnabled) "Desactivar pantalla Home" else "Activar pantalla Home") }, { menu = false; s.toggleHome() })
                            DropdownMenuItem({ Text("Launcher predeterminado…") }, { menu = false; openHomeSettings(ctx) })
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(Color(0x40FFFFFF)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Buscar grupos y apps…", color = Color.White.copy(.6f))
                        BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White))
                    }
                    if (query.isNotEmpty()) Icon(Icons.Default.Close, "Borrar", tint = Color.White, modifier = Modifier.clickable { query = "" })
                }
            }
            if (s.groups.isEmpty()) item {
                Text("Pulsa ✎ y luego + para crear tu primer grupo.", color = Color.White.copy(.8f))
            } else if (shown.isEmpty() && ungrouped.isEmpty()) item {
                Text("Sin resultados para “$query”.", color = Color.White.copy(.8f))
            }
            items(shown, key = { it.id }) { g -> GroupCard(g, s.groups.indexOf(g), s, apps, onDlg = { dlg = it }) }
            if (ungrouped.isNotEmpty()) item {
                val u = s.ung
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xCC000000L or u.color)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) {
                            Text("${sel.size} seleccionadas", color = Color.White, modifier = Modifier.weight(1f))
                            TextButton({ sel = ungrouped.map { it.pkg }.toSet() }) { Text("Todas") }
                            TextButton({ if (sel.isNotEmpty()) dlg = Dlg.AddTo(sel.toList()) }) { Text("Añadir…") }
                            IconButton({ selecting = false; sel = emptySet() }) { Icon(Icons.Default.Close, "Cancelar", tint = Color.White) }
                        } else {
                            Text("Sin grupo", fontSize = 20.sp, color = Color.White, fontFamily = Fonts.get(ctx, u.font), modifier = Modifier.weight(1f))
                            MiniBtn(Icons.Default.Settings) { dlg = Dlg.Settings(u.id) }
                        }
                    }
                    Text("Mantén pulsada una app para añadirla o seleccionar varias", fontSize = 12.sp, color = Color.White.copy(.6f))
                    Spacer(Modifier.height(8.dp))
                    AppGrid(ungrouped.map { it.pkg }, u, apps, sel = selObj, onAdd = { dlg = Dlg.AddTo(listOf(it)) })
                }
            }
        }
    }
    DialogHost(dlg, { dlg = null }, s, apps) { selecting = false; sel = emptySet() }
}

@Composable
fun GroupCard(g: Group, idx: Int, s: LauncherState, apps: Map<String, AppInfo>, onDlg: (Dlg) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xCC000000L or g.color)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, fontSize = 20.sp, color = Color.White, fontFamily = Fonts.get(ctx, g.font),
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.edit) {
                MiniBtn(Icons.Default.KeyboardArrowUp) { s.move(idx, -1) }
                MiniBtn(Icons.Default.KeyboardArrowDown) { s.move(idx, 1) }
                MiniBtn(Icons.Default.Settings) { onDlg(Dlg.Settings(g.id)) }
                MiniBtn(Icons.Default.Edit) { onDlg(Dlg.Rename(g.id, g.name)) }
                MiniBtn(Icons.Default.Delete) { s.remove(g.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
        AppGrid(g.apps, g, apps, onMove = { p, d -> s.upd(g.id) { it.copy(apps = it.apps.moved(p, d)) } }) { p -> s.upd(g.id) { it.copy(apps = it.apps - p) } }
        g.subs.forEach { sub -> SubSection(sub, g, s, apps, onDlg) }
        if (s.edit) Row {
            TextButton({ onDlg(Dlg.Pick(g.id)) }) { Text("+ Apps") }
            TextButton({ onDlg(Dlg.NewSub(g.id)) }) { Text("+ Subgrupo") }
        }
    }
}

@Composable
fun SubSection(sub: Group, parent: Group, s: LauncherState, apps: Map<String, AppInfo>, onDlg: (Dlg) -> Unit) {
    var open by remember { mutableStateOf(true) }
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)).padding(10.dp)) {
        Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null, tint = Color.White.copy(.7f))
            Text(sub.name, color = Color.White.copy(.9f), fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (s.edit) {
                MiniBtn(Icons.Default.Add) { onDlg(Dlg.Pick(sub.id)) }
                MiniBtn(Icons.Default.Edit) { onDlg(Dlg.Rename(sub.id, sub.name)) }
                MiniBtn(Icons.Default.Delete) { s.remove(sub.id) }
            }
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            AppGrid(sub.apps, parent, apps, onMove = { p, d -> s.upd(sub.id) { it.copy(apps = it.apps.moved(p, d)) } }) { p -> s.upd(sub.id) { it.copy(apps = it.apps - p) } }
        }
    }
}

@Composable
fun MiniBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) =
    IconButton(onClick, Modifier.size(36.dp)) { Icon(icon, null, tint = Color.White.copy(.85f), modifier = Modifier.size(20.dp)) }

/** Cuadrícula paginada: cols × rows por página, deslizable horizontalmente. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGrid(
    pkgs: List<String>, cfg: Group, apps: Map<String, AppInfo>, sel: Sel? = null,
    onAdd: ((String) -> Unit)? = null, onMove: ((String, Int) -> Unit)? = null, onRemove: ((String) -> Unit)? = null,
) {
    val list = pkgs.mapNotNull { apps[it] }
    if (list.isEmpty()) { Text("Vacío", color = Color.White.copy(.5f), fontSize = 13.sp); return }
    val pages = list.chunked(cfg.cols * cfg.rows)
    val pager = rememberPagerState { pages.size }
    val cellH = (cfg.size + 30).dp
    HorizontalPager(pager, Modifier.fillMaxWidth().height(cellH * cfg.rows)) { p ->
        Column {
            pages[p].chunked(cfg.cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(cellH)) {
                    row.forEach { a -> Box(Modifier.weight(1f)) { Cell(a, cfg.size, onRemove, onAdd, onMove, sel) } }
                    repeat(cfg.cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.Center) {
        repeat(pages.size) {
            Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape)
                .background(if (it == pager.currentPage) Color.White else Color.White.copy(.3f)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Cell(a: AppInfo, size: Int, onRemove: ((String) -> Unit)?, onAdd: ((String) -> Unit)?, onMove: ((String, Int) -> Unit)?, sel: Sel?) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val selecting = sel?.on == true
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).combinedClickable(
            onClick = { if (selecting) sel!!.toggle(a.pkg) else ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let(ctx::startActivity) },
            onLongClick = { if (selecting) sel!!.toggle(a.pkg) else menu = true },
        ).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Image(a.icon, a.label, Modifier.size(size.dp))
            val n = Badges.counts[a.pkg] ?: 0
            if (n > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp).defaultMinSize(18.dp, 18.dp)
                    .clip(CircleShape).background(Color(0xFFE53935)).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 10.sp) }
            if (selecting && a.pkg in sel!!.set) Box(
                Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(Color(0xFF8AB4F8)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
        }
        Text(a.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        DropdownMenu(menu, { menu = false }) {
            if (onMove != null) {
                DropdownMenuItem({ Text("◀ Mover atrás") }, { menu = false; onMove(a.pkg, -1) })
                DropdownMenuItem({ Text("Mover adelante ▶") }, { menu = false; onMove(a.pkg, 1) })
            }
            if (onRemove != null) DropdownMenuItem({ Text("Quitar") }, { menu = false; onRemove(a.pkg) })
            if (onAdd != null) DropdownMenuItem({ Text("Añadir a un grupo…") }, { menu = false; onAdd(a.pkg) })
            if (sel != null) DropdownMenuItem({ Text("Seleccionar varias") }, { menu = false; sel.start(a.pkg) })
            DropdownMenuItem({ Text("Info de la app") }, {
                menu = false
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
            })
        }
    }
}

// ───────────── DIÁLOGOS ─────────────
@Composable
fun DialogHost(d: Dlg?, close: () -> Unit, s: LauncherState, apps: Map<String, AppInfo>, onAdded: () -> Unit = {}) {
    when (d) {
        null -> {}
        Dlg.NewGroup -> NameDialog("Nuevo grupo", "", close) { n -> s.set(s.groups + Group(name = n)) }
        is Dlg.NewSub -> NameDialog("Nuevo subgrupo", "", close) { n -> s.upd(d.parent) { it.copy(subs = it.subs + Group(name = n)) } }
        is Dlg.Rename -> NameDialog("Renombrar", d.name, close) { n -> s.upd(d.id) { it.copy(name = n) } }
        is Dlg.Settings -> s.find(d.id)?.let { SettingsDialog(it, s, close) }
        is Dlg.Pick -> {
            val g = s.find(d.id) ?: return
            var sel by remember { mutableStateOf(g.apps) }
            var q by remember { mutableStateOf("") }
            val list = apps.values.filter { q.isBlank() || it.label.contains(q, true) }.sortedBy { it.label.lowercase() }
            AlertDialog(onDismissRequest = close,
                modifier = Modifier.fillMaxWidth(0.94f),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                title = { Text("Apps en ${g.name}") },
                confirmButton = { TextButton({ s.upd(g.id) { it.copy(apps = sel) }; close() }) { Text("Guardar") } },
                dismissButton = { TextButton(close) { Text("Cancelar") } },
                text = {
                    Column {
                        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Buscar app…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = if (q.isNotEmpty()) ({ IconButton({ q = "" }) { Icon(Icons.Default.Close, null) } }) else null)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 480.dp)) {
                            items(list, key = { it.pkg }) { a ->
                                val on = a.pkg in sel
                                Row(Modifier.fillMaxWidth().clickable { sel = if (on) sel - a.pkg else sel + a.pkg }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(on, null)
                                    Spacer(Modifier.width(8.dp))
                                    Image(a.icon, null, Modifier.size(48.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Text(a.label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                })
        }
        is Dlg.AddTo -> {
            var creating by remember { mutableStateOf(false) }
            if (creating) NameDialog("Nuevo grupo", "", close) { n -> s.set(s.groups + Group(name = n, apps = d.pkgs)); onAdded() }
            else AlertDialog(onDismissRequest = close,
                confirmButton = { TextButton(close) { Text("Cancelar") } },
                title = { Text(if (d.pkgs.size > 1) "Añadir ${d.pkgs.size} apps a…" else "Añadir a…") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        item { AddRow("＋ Crear grupo nuevo", 0) { creating = true } }
                        s.groups.forEach { g ->
                            item { AddRow(g.name, 0) { s.upd(g.id) { it.copy(apps = (it.apps + d.pkgs).distinct()) }; onAdded(); close() } }
                            g.subs.forEach { sg ->
                                item { AddRow("↳ ${sg.name}", 20) { s.upd(sg.id) { it.copy(apps = (it.apps + d.pkgs).distinct()) }; onAdded(); close() } }
                            }
                        }
                    }
                })
        }
    }
}

@Composable
fun SettingsDialog(g: Group, s: LauncherState, close: () -> Unit) {
    val ctx = LocalContext.current
    val isHome = s.home.any { it.id == g.id }
    fun up(f: (Group) -> Group) = s.upd(g.id, f)
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(close) { Text("Listo") } },
        title = { Text("Ajustes: ${g.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Sl("Columnas", g.cols, 2..8) { v -> up { it.copy(cols = v) } }
                Sl("Filas", g.rows, 1..(if (isHome) 10 else 8)) { v -> up { it.copy(rows = v) } }
                Sl("Tamaño de icono", g.size, 32..96) { v -> up { it.copy(size = v) } }
                Text("Tipografía del título", fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
                    Fonts.names.forEachIndexed { i, n ->
                        val on = g.font == i
                        Text(n, fontFamily = Fonts.get(ctx, i), fontSize = 18.sp, color = if (on) Color.Black else Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF8AB4F8) else Color(0x33FFFFFF))
                                .clickable { up { it.copy(font = i) } }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                if (!isHome) {
                    Text("Color del fondo", fontSize = 14.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
                        Palette.forEach { c ->
                            val on = g.color == c
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF000000L or c))
                                .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color.Gray, CircleShape)
                                .clickable { up { it.copy(color = c) } })
                        }
                    }
                }
            }
        })
}

@Composable
fun Sl(label: String, v: Int, r: IntRange, on: (Int) -> Unit) {
    Column {
        Text("$label: $v", fontSize = 14.sp)
        Slider(v.toFloat(), { on(it.toInt()) }, valueRange = r.first.toFloat()..r.last.toFloat(), steps = r.last - r.first - 1)
    }
}

@Composable
fun NameDialog(title: String, init: String, close: () -> Unit, ok: (String) -> Unit) {
    var t by remember { mutableStateOf(init) }
    AlertDialog(onDismissRequest = close, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton({ if (t.isNotBlank()) { ok(t.trim()); close() } }) { Text("Aceptar") } },
        dismissButton = { TextButton(close) { Text("Cancelar") } })
}

@Composable
fun AddRow(label: String, indent: Int, onClick: () -> Unit) =
    Text(label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(start = indent.dp, top = 12.dp, bottom = 12.dp))
