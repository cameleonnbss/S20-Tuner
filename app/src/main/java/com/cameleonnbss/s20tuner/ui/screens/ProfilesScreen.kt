package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.TunerViewModel

@Composable
fun ProfilesScreen(vm: TunerViewModel) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    ScreenScaffold("Profiles", "Save the current configuration as a reusable JSON profile") {

        SectionCard("Presets") {
            TunerViewModel.presets.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Row {
                        ActionButton("Apply") { vm.applyPreset(p) }
                    }
                }
            }
        }

        SectionCard("My profiles (${profiles.size})") {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it },
                label = { Text("New profile name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            ActionButton("Save current config as profile", modifier = Modifier.fillMaxWidth(), primary = true) { if (newName.isNotBlank()) vm.saveProfile(newName.trim()) }
            profiles.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Row {
                        ActionButton("Apply") { vm.loadProfile(p) }
                        Spacer(Modifier.width(6.dp))
                        ActionButton("Delete") { vm.deleteProfile(p) }
                    }
                    // p.isAuto unused for now
                }
            }
        }

        SectionCard("Backup / restore (JSON)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Export to /sdcard/S20Tuner") { vm.exportProfiles() }
                ActionButton("Import from /sdcard/S20Tuner") { vm.importProfiles() }
            }
            Text("Profiles are plain JSON — you can edit them by hand and share them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        SectionCard("Boot persistence") {
            val ui by vm.ui.collectAsStateWithLifecycle()
            ToggleRow("Re-apply config at boot", ui.bootPersistEnabled, { vm.setBootPersist(it) },
                subtitle = "Installs /data/adb/service.d/s20tuner_boot.sh")
        }
    }
}
