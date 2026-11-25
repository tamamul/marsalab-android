package com.marsa.lab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(vm: AppViewModel) {
    val ui by vm.login.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf(ui.baseUrl) }
    var user by rememberSaveable { mutableStateOf(ui.username) }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun submit() {
        focus.clearFocus()
        vm.login(server, user, pass)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MARSA", color = MarsaColors.Cyan, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text("Pemantau Lab Komputer", color = MarsaColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Alamat server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Nama pengguna") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Kata sandi") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                TextButton(onClick = { showPass = !showPass }) {
                    Text(if (showPass) "Sembunyikan" else "Lihat", style = MaterialTheme.typography.labelMedium)
                }
            },
        )

        ui.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { submit() },
            enabled = !ui.busy && server.isNotBlank() && user.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (ui.busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MarsaColors.OnCyan)
            } else {
                Text("Masuk", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Gunakan akun guru yang dibuat di gateway. Kata sandi tidak disimpan di perangkat.",
            color = MarsaColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
