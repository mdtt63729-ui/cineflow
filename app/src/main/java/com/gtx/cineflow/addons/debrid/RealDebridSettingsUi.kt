package com.gtx.cineflow.addons.debrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Embeddable settings section; parent screen owns navigation and app theme. */
@Composable
fun RealDebridSettingsSection(
    tokenStore: EncryptedTokenStore,
    client: RealDebridClient,
    modifier: Modifier = Modifier,
) {
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(tokenStore.read() != null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Real-Debrid")
        Text("Store your own API token securely on this device. Use only links for content you are authorized to access.")
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.take(4096); status = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (saved) "New API token (optional)" else "API token") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && token.isNotBlank(), onClick = {
                runCatching { tokenStore.save(token) }.onSuccess {
                    saved = true; token = ""; status = "Token saved encrypted on this device."
                }.onFailure { status = "Could not save token securely on this device." }
            }) { Text("Save token") }
            Button(enabled = !busy && saved, onClick = {
                busy = true; status = null
                scope.launch {
                    status = try {
                        val user = client.getUser()
                        "Connected${user.username?.let { ": $it" } ?: ""}. Account check successful."
                    } catch (e: Exception) { e.message ?: "Connection check failed." }
                    busy = false
                }
            }) { Text("Test connection") }
            TextButton(enabled = !busy && saved, onClick = {
                tokenStore.clear(); saved = false; token = ""; status = "Saved token cleared."
            }) { Text("Remove") }
        }
        if (busy) CircularProgressIndicator()
        status?.let { Text(it) }
    }
}
