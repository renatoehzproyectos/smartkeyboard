package com.smartkeyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartkeyboard.ui.SetupButtons
import com.smartkeyboard.ui.SettingsActivity
import com.smartkeyboard.ui.SmartTheme

/** Asistente de activación del teclado. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartTheme {
                val ctx = LocalContext.current
                var test by remember { mutableStateOf("") }
                Column(
                    Modifier.windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Smart Keyboard", style = MaterialTheme.typography.headlineLarge)
                    Text("Teclado predictivo en español que no necesita espacios. Sigue estos pasos:", style = MaterialTheme.typography.bodyLarge)
                    SetupButtons()
                    Text("3. Pruébalo aquí:", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = test, onValueChange = { test = it }, label = { Text("Escribe: holacomoestas") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Ajustes y privacidad") }
                }
            }
        }
    }
}
