package com.gtx.cineflow.addons.cloudstream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A small reusable Material 3 panel; caller owns file picker and inspection state. */
@Composable
fun Cs3CompatibilityPanel(
    result: Cs3InspectionResult?,
    onChooseArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cloudstream compatibility", style = MaterialTheme.typography.titleLarge)
        Text(
            "Inspect a .cs3 archive's metadata. CineFlow does not execute third-party plugin code.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = onChooseArchive, modifier = Modifier.fillMaxWidth()) {
            Text("Choose .cs3 archive")
        }
        result?.let { inspection ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(inspection.status.name.replace('_', ' '), style = MaterialTheme.typography.labelLarge)
                    Text(inspection.message, style = MaterialTheme.typography.bodyMedium)
                    inspection.metadata?.let { meta ->
                        Text(meta.name, style = MaterialTheme.typography.titleMedium)
                        meta.version?.let { Text("Version: $it") }
                        meta.description?.let { Text(it) }
                        if (meta.authors.isNotEmpty()) Text("Authors: ${meta.authors.joinToString()}")
                        meta.language?.let { Text("Language: $it") }
                    }
                    inspection.warnings.forEach { warning ->
                        Row { Text("• $warning", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
