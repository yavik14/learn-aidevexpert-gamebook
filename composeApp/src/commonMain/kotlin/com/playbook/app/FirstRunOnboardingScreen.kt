package com.playbook.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Primera superficie de la app cuando todavía no hay Proyecto: onboarding con la
 * cabecera de marca (icono + "Playbook", sin Back). No hay splash nativo ni
 * pantalla intermedia (ver `DESIGN.md`).
 */
@Composable
fun FirstRunOnboardingScreen(
    onStartBrainstorming: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PlaybookBrandHeader() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Tu Proyecto de Juego",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Playbook organiza tus Notas y tu GDD en un Proyecto único. " +
                    "Puedes empezar con el Brainstorming guiado o crear el Proyecto " +
                    "vacío y completarlo después.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartBrainstorming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Iniciar Brainstorming")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Saltar")
            }
        }
    }
}

/** Estado de carga inicial del Proyecto, también con la cabecera de marca. */
@Composable
fun FirstRunLoading(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PlaybookBrandHeader() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = "Cargando…" },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cargando…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
