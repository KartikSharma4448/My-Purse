package com.mypurse.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PrivacyPolicyButton() {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { visible = true }) {
        Icon(Icons.Rounded.PrivacyTip, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Privacy policy")
    }
    if (visible) {
        val context = LocalContext.current
        val policy = remember(context) {
            context.assets.open("privacy-policy.txt").bufferedReader().use { it.readText() }
        }
        Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 720.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Privacy policy", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(policy, modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { visible = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}
