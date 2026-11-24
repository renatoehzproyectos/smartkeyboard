package com.smartkeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartkeyboard.Prefs
import com.smartkeyboard.SmartKeyboardApp
import com.smartkeyboard.engine.EngineConfig

@Composable
fun SmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartTheme { SettingsScreen() } }
    }
}

@Composable
fun SetupButtons() {
    val ctx = LocalContext.current
    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) {
        Text("1. Activar el teclado en Ajustes")
    }
    OutlinedButton(onClick = { (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }, modifier = Modifier.fillMaxWidth()) {
        Text("2. Seleccionar Smart Keyboard")
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, key: String, default: Boolean) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.get(ctx).getBoolean(key, default)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = on, onCheckedChange = { on = it; Prefs.get(ctx).edit().putBoolean(key, it).apply() })
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val learning = remember { SmartKeyboardApp.of(ctx).learning }
    var beam by remember { mutableFloatStateOf(Prefs.get(ctx).getInt(Prefs.BEAM, EngineConfig().beamWidth).toFloat()) }
    var confirm by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf("") }

    Column(
        Modifier.windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Smart Keyboard", style = MaterialTheme.typography.headlineMedium)
        Text("Escribe sin espacios: el teclado detecta las palabras, las completa y los inserta por ti. Todo el procesamiento es local.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        SetupButtons()
        OutlinedTextField(value = test, onValueChange = { test = it }, label = { Text("Prueba aquí (p. ej. quieroiralcine)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SwitchRow("Predicción continua", "Interpretar lo escrito sin espacios", Prefs.PREDICTIONS, true)
        SwitchRow("Aprendizaje personal", "Aprender tu vocabulario y tus elecciones (solo en este dispositivo). Desactívalo para pausar.", Prefs.LEARNING, true)
        SwitchRow("Vibración al teclear", "", Prefs.HAPTIC, true)
        SwitchRow("Vista previa sobre el teclado", "Muestra lo que escribes con animaciones suaves justo encima del teclado.", Prefs.PREVIEW, true)
        SwitchRow("Registro de depuración", "Guarda cada acción de escritura en memoria (campos protegidos enmascarados). Se inicia y copia con el botón 🐞 del teclado.", Prefs.DEBUG, false)
        Text("Anchura del Beam Search: ${beam.toInt()}", style = MaterialTheme.typography.titleMedium)
        Slider(value = beam, onValueChange = { beam = it }, valueRange = 4f..32f, steps = 27,
            onValueChangeFinished = { Prefs.get(ctx).edit().putInt(Prefs.BEAM, beam.toInt()).apply() })
        Text("Más anchura = más precisión y algo más de CPU.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Aprendido: ${learning.learnedWords} palabras, ${learning.learnedPairs} combinaciones, ${learning.vocabulary.size()} palabras personales, ${learning.corrections.total} correcciones.", style = MaterialTheme.typography.bodyMedium)
        Text("Nunca se aprende de contraseñas, correo, URL, números ni campos que lo prohíban.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Borrar todos los datos personalizados") }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Borrar el aprendizaje?") },
        text = { Text("Se eliminarán vocabulario, frecuencias, combinaciones, expresiones y correcciones. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { learning.clearAll(); confirm = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}
