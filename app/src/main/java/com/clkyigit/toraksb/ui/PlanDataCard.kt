package com.clkyigit.toraksb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clkyigit.toraksb.data.StudyLog
import com.clkyigit.toraksb.geometry.NeedlePlan

/** Plana giren tüm veriler: doktorun girdikleri ve fotoğraftan çıkanlar. Katlanabilir. */
@Composable
fun PlanDataCard(plan: NeedlePlan, extra: Map<String, String> = emptyMap()) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rows = (plan.details + extra).filterValues { it.isNotEmpty() }
    Card {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(onClick = { open = !open }) {
                Text(if (open) "Plan verilerini gizle" else "Plan verileri (${rows.size})")
            }
            if (open) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StudyLog.COLUMNS.forEach { (key, label) ->
                        val v = rows[key] ?: return@forEach
                        Row(Modifier.fillMaxWidth()) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
