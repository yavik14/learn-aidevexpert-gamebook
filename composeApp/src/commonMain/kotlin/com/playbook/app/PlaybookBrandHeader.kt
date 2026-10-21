package com.playbook.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.playbook.app.resources.Res
import com.playbook.app.resources.playbook_icon
import org.jetbrains.compose.resources.painterResource

/** Altura mínima de la cabecera (sin contar la status bar). */
private val HEADER_MIN_HEIGHT = 56.dp

/** Tamaño del icono en modo marca (onboarding/principal). */
private val BRAND_ICON_SIZE = 28.dp

/** Tamaño del icono en modo detalle (descripción de 24–28dp). */
private val DETAIL_ICON_SIZE = 24.dp

/**
 * Cabecera común de Playbook.
 *
 * - **Marca** (`title == null`): icono + "Playbook" + acciones. Se usa en el
 *   onboarding y en la pantalla principal.
 * - **Detalle** (`title != null`): Back ("Volver", 48dp) → icono → título, con
 *   máximo 2 líneas y ellipsis. Se usa en todas las pantallas secundarias.
 *
 * El icono es **decorativo** (`contentDescription = null`) porque el wordmark o
 * el título ya comunican la pantalla; el Back expone un único
 * `contentDescription = "Volver"` y siempre ejecuta el `onBack` recibido.
 * El contenedor consume la status bar una sola vez y está pensado para usarse
 * como `topBar` de un `Scaffold`; no añadir otro `statusBarsPadding` debajo.
 */
@Composable
fun PlaybookBrandHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = HEADER_MIN_HEIGHT)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button, onClick = onBack)
                        .clearAndSetSemantics {
                            contentDescription = "Volver"
                            role = Role.Button
                            this.onClick {
                                onBack()
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
            PlaybookHeaderIcon(
                size = if (title == null) BRAND_ICON_SIZE else DETAIL_ICON_SIZE,
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (title == null) {
                Text(
                    text = "Playbook",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions()
        }
    }
}

/**
 * Icono de marca de la cabecera. No es el icono de launcher: es un recurso
 * versionado (`playbook_icon.png`) y siempre decorativo.
 */
@Composable
fun PlaybookHeaderIcon(size: Dp) {
    Image(
        painter = painterResource(Res.drawable.playbook_icon),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp)),
    )
}
