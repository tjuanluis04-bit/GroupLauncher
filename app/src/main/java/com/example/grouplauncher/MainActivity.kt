package com.example.grouplauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
}

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = LauncherState(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8AB4F8))) {
                var apps by remember { mutableStateOf(emptyMap<String, AppInfo>()) }
                LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps() } }
                Root(state, apps)
            }
        }
    }

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

/** Colores de fondo de las tarjetas (la opacidad es siempre la misma: 0xCC). */
val Palette = listOf<Long>(0x15171C, 0x1E1B4B, 0x4A1D6B, 0x831843, 0x7F1D1D, 0x78350F, 0x1B4332, 0x134E4A, 0x0C4A6E, 0x374151)

fun openHomeSettings(ctx: Context) {
    try { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

@Composable
fun Root(s: LauncherState, apps: Map<String, AppInfo>) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val shown = if (query.isBlank()) s.groups else s.groups.filter { g ->
        g.name.contains(query, true) || g.subs.any { it.name.contains(query, true) }
    }
    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f).focusRequester(fr),
                            singleLine = true, placeholder = { Text("Buscar grupo…") })
                        LaunchedEffect(Unit) { fr.requestFocus() }
                        IconButton({ searching = false; query = "" }) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
                    } else {
                        Text("Mis grupos", fontSize = 26.sp, color = Color.White, modifier = Modifier.weight(1f))
                        IconButton({ searching = true }) { Icon(Icons.Default.Search, "Buscar", tint = Color.White) }
                        if (s.edit) FilledTonalIconButton({ dlg = Dlg.NewGroup }) { Icon(Icons.Default.Add, "Nuevo grupo") }
                        IconButton({ s.edit = !s.edit }) {
                            Icon(if (s.edit) Icons.Default.Check else Icons.Default.Edit, "Editar", tint = Color.White)
                        }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Menú", tint = Color.White) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Launcher predeterminado…") }, { menu = false; openHomeSettings(ctx) })
                            }
                        }
                    }
                }
            }
            if (s.groups.isEmpty()) item {
                Text("Pulsa ✎ y luego + para crear tu primer grupo.", color = Color.White.copy(.8f))
            } else if (shown.isEmpty()) item {
                Text("Sin resultados para “$query”.", color = Color.White.copy(.8f))
            }
            items(shown, key = { it.id }) { g ->
                GroupCard(g, s.groups.indexOf(g), s, apps, onDlg = { dlg = it })
            }
        }
    }
    DialogHost(dlg, { dlg = null }, s, apps)
}

@Composable
fun GroupCard(g: Group, idx: Int, s: LauncherState, apps: Map<String, AppInfo>, onDlg: (Dlg) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xCC000000L or g.color)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, fontSize = 18.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.edit) {
                MiniBtn(Icons.Default.KeyboardArrowUp) { s.move(idx, -1) }
                MiniBtn(Icons.Default.KeyboardArrowDown) { s.move(idx, 1) }
                MiniBtn(Icons.Default.Settings) { onDlg(Dlg.Settings(g.id)) }
                MiniBtn(Icons.Default.Edit) { onDlg(Dlg.Rename(g.id, g.name)) }
                MiniBtn(Icons.Default.Delete) { s.remove(g.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
        AppGrid(g.apps, g, apps) { p -> s.upd(g.id) { it.copy(apps = it.apps - p) } }
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
            AppGrid(sub.apps, parent, apps) { p -> s.upd(sub.id) { it.copy(apps = it.apps - p) } }
        }
    }
}

@Composable
fun MiniBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) =
    IconButton(onClick, Modifier.size(36.dp)) { Icon(icon, null, tint = Color.White.copy(.85f), modifier = Modifier.size(20.dp)) }

/** Cuadrícula paginada: cols × rows por página, deslizable horizontalmente. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGrid(pkgs: List<String>, cfg: Group, apps: Map<String, AppInfo>, onRemove: (String) -> Unit) {
    val list = pkgs.mapNotNull { apps[it] }
    if (list.isEmpty()) { Text("Vacío", color = Color.White.copy(.5f), fontSize = 13.sp); return }
    val pages = list.chunked(cfg.cols * cfg.rows)
    val pager = rememberPagerState { pages.size }
    val cellH = (cfg.size + 30).dp
    HorizontalPager(pager, Modifier.fillMaxWidth().height(cellH * cfg.rows)) { p ->
        Column {
            pages[p].chunked(cfg.cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(cellH)) {
                    row.forEach { a -> Box(Modifier.weight(1f)) { Cell(a, cfg.size, onRemove) } }
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
fun Cell(a: AppInfo, size: Int, onRemove: (String) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).combinedClickable(
            onClick = { ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let(ctx::startActivity) },
            onLongClick = { menu = true },
        ).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Image(a.icon, a.label, Modifier.size(size.dp))
        Text(a.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Quitar del grupo") }, { menu = false; onRemove(a.pkg) })
            DropdownMenuItem({ Text("Info de la app") }, {
                menu = false
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
            })
        }
    }
}

@Composable
fun DialogHost(d: Dlg?, close: () -> Unit, s: LauncherState, apps: Map<String, AppInfo>) {
    when (d) {
        null -> {}
        Dlg.NewGroup -> NameDialog("Nuevo grupo", "", close) { n -> s.set(s.groups + Group(name = n)) }
        is Dlg.NewSub -> NameDialog("Nuevo subgrupo", "", close) { n -> s.upd(d.parent) { it.copy(subs = it.subs + Group(name = n)) } }
        is Dlg.Rename -> NameDialog("Renombrar", d.name, close) { n -> s.upd(d.id) { it.copy(name = n) } }
        is Dlg.Settings -> s.groups.find { it.id == d.id }?.let { g ->
            AlertDialog(onDismissRequest = close, confirmButton = { TextButton(close) { Text("Listo") } },
                title = { Text("Ajustes: ${g.name}") },
                text = {
                    Column {
                        Sl("Columnas", g.cols, 2..8) { v -> s.upd(g.id) { it.copy(cols = v) } }
                        Sl("Filas por página", g.rows, 1..6) { v -> s.upd(g.id) { it.copy(rows = v) } }
                        Sl("Tamaño de icono", g.size, 32..96) { v -> s.upd(g.id) { it.copy(size = v) } }
                        Text("Color del fondo", fontSize = 14.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
                            Palette.forEach { c ->
                                val on = g.color == c
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF000000L or c))
                                    .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color.Gray, CircleShape)
                                    .clickable { s.upd(g.id) { it.copy(color = c) } })
                            }
                        }
                    }
                })
        }
        is Dlg.Pick -> {
            val g = (s.groups + s.groups.flatMap { it.subs }).find { it.id == d.id } ?: return
            var sel by remember { mutableStateOf(g.apps) }
            AlertDialog(onDismissRequest = close,
                title = { Text("Apps en ${g.name}") },
                confirmButton = { TextButton({ s.upd(g.id) { it.copy(apps = sel) }; close() }) { Text("Guardar") } },
                dismissButton = { TextButton(close) { Text("Cancelar") } },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(apps.values.sortedBy { it.label.lowercase() }, key = { it.pkg }) { a ->
                            val on = a.pkg in sel
                            Row(Modifier.fillMaxWidth().clickable { sel = if (on) sel - a.pkg else sel + a.pkg }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(on, null)
                                Image(a.icon, null, Modifier.size(32.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                })
        }
    }
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
