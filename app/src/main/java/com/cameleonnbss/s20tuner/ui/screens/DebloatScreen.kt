package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.data.DebloatList
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.TunerViewModel

@Composable
fun DebloatScreen(vm: TunerViewModel) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var mode by remember { mutableStateOf("freeze") } // freeze | remove
    var installed by remember { mutableStateOf<List<String>?>(null) }
    var confirmPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.listThirdParty { installed = it } }

    val entries = DebloatList.entries.filter { e ->
        (category == "All" || e.category == category) &&
        (query.isBlank() || e.pkg.contains(query, true) || e.label.contains(query, true))
    }

    ScreenScaffold("Debloat", "Freeze (reversible) or remove per-user — ${DebloatList.entries.size} curated packages") {

        SectionCard("Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("freeze", "remove").forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m },
                        label = { Text(if (m == "freeze") "Freeze (reversible)" else "Remove (user 0)") })
                }
            }
        }

        SectionCard("Filter") {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search package or name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                DebloatList.categories.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        SectionCard("Packages (${entries.size})") {
            if (installed != null) {
                Text("Loaded ${installed!!.size} user-installed packages for status icons.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            entries.forEach { e ->
                val isInstalled = installed?.contains(e.pkg)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.label, style = MaterialTheme.typography.bodyMedium)
                        Text(e.pkg, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                    if (e.risk == "Caution") {
                        Text("⚠", color = Amber, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isInstalled == false || mode == "remove") {
                            ActionButton("Restore") { vm.restorePackage(e.pkg) }
                        }
                        ActionButton(if (mode == "freeze") "Freeze" else "Remove") { confirmPkg = e.pkg }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    confirmPkg?.let { pkg ->
        AlertDialog(
            onDismissRequest = { confirmPkg = null },
            title = { Text("Confirm ${if (mode == "freeze") "freeze" else "removal"}") },
            text = { Text("$pkg\n\n${if (mode == "remove") "Removal is per-user (user 0) — restorable with Restore. A factory reset may be needed if you remove critical system UI." else "Freeze keeps the app installed but disabled."}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.debloat(pkg, mode == "freeze")
                    confirmPkg = null
                }) { Text("Confirm", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmPkg = null }) { Text("Cancel") } }
        )
    }
}
