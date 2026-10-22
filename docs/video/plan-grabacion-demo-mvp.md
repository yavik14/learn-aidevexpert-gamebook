# Plan de grabación del vídeo demo del MVP con IA

- **Versión:** 1.0
- **Fecha de planificación:** 2026-10-22
- **Feature:** `video-script-mvp-ai` (spec: [`docs/specs/video-script-mvp-ai.md`](../specs/video-script-mvp-ai.md))
- **Guion:** [guion-demo-mvp.md](guion-demo-mvp.md)
- **Storyboard:** [storyboard-demo-mvp.md](storyboard-demo-mvp.md)
- **Objetivo:** grabar una pieza de 13:30 de contenido, 14:10 con cierre y nunca más de
  15:00, con demo Android de **IA real** y datos generados **en vivo**.
- **Estado:** plan listo; el vídeo no se ha grabado en esta tarea.

> **Regla que no se negocia:** la demo se graba con IA real. El modo fake se usa para el
> gate y los tests, se explica en el vídeo, pero **no se graba como si fuera real**. Si la
> IA real falla, se detiene la toma y se reprograma o se usa un clip real ya grabado. Nunca
> se simula una respuesta, una imagen, un audio ni una transcripción.

---

## 1. Decisiones cerradas y pendientes

| Tema | Decisión |
|---|---|
| Audiencia | Evaluador técnico del curso |
| Idioma y voz | Español de España, primera persona, sin fundamentos |
| Duración | 13:30 de contenido; 14:10 con cierre; 15:00 de límite; 10:00 de mínimo |
| Chat | Gemini, nombrado; sin pantalla salvo que el autor lo pida |
| Herramientas | OpenCode Desktop + VS Code sin etiqueta estricta; Android Studio para validación y demo |
| Dispositivo | Google Pixel 8 Pro con Android 17, vertical nativo |
| Captura | Ventana de dispositivo de Android Studio; fallback a grabadora del Pixel o `adb screenrecord` |
| Demo | IA real, datos en vivo, sin precargar Proyecto ni Notas |
| Superficies | Onboarding/Proyecto, Brainstorming, Nota, GDD, Q&A, relacionadas e imagen |
| Fuera de la demo | Dictado inline y transcripción de Adjuntos; no se simulan |
| Smoke | `RealAiSmokeTest` key-gated: smoke completo de seis modelos; si falla `transcribe`, pre-flight manual de tts/image/jev |
| Publicación | WeTransfer, sin música |
| Pendiente | Nombre final del archivo, resolución maestra y si Gemini aparece en pantalla |

Las decisiones de contenido y tiempos están en el [guion §8](guion-demo-mvp.md#8-confirmaciones-del-autor).
Las pendientes se cierran antes del ensayo técnico, no durante la grabación.

---

## 2. Pre-flight técnico

### 2.1 Máquina y toolchain

| Comprobación | Comando o acción | Criterio |
|---|---|---|
| Directorio | `pwd` | Estás en el worktree de la feature, no en el checkout principal |
| JDK 21 | `java -version` | 21 o superior; el release exige 21 |
| Android SDK | `echo "$ANDROID_HOME"` y `ls "$ANDROID_HOME/platform-tools"` | Ruta válida y `adb` presente |
| `adb` | `adb version` | Responde sin error |
| Gradle | `./gradlew --version` | Usa el wrapper del repo |
| Gate | `CI=true ./init.sh` | Exit 0. Es Android-only y usa IA fake; no instala |
| Espacio | `df -h .` | ≥ 10 GB libres para clips y exports |
| Red | Prueba de conexión del proveedor | Estable; si falla, no se graba la demo |

El gate se ejecuta **antes** del build real. No se usa `init.sh` para instalar: fuerza el
modo fake y la demo quedaría falseada. El orden es gate → build real → smoke → datos limpios
→ grabación.

### 2.2 Dispositivo

| Comprobación | Comando o acción | Criterio |
|---|---|---|
| Conexión USB | `adb devices -l` | Un dispositivo `device`, no `unauthorized` |
| Modelo | `adb shell getprop ro.product.model` | **Pixel 8 Pro** (o el modelo confirmado) |
| Versión | `adb shell getprop ro.build.version.release` | **Android 17** |
| Pantalla | `adb shell wm size` | Vertical; anotar resolución nativa |
| Rotación | Bloquear en vertical desde el panel o `adb shell settings put system accelerometer_rotation 0` | La app no rota durante la toma |
| Idioma | `adb shell settings get system system_locales` | `es-ES` |
| Font scale | `adb shell settings get system font_scale` | `1.0` |
| Notificaciones | Activar “No molestar” desde el panel | Ninguna notificación entra en plano |
| Batería | `adb shell dumpsys battery` | > 80 % o enchufado |
| Almacenamiento | `adb shell df -h /data` | ≥ 5 GB libres |
| Hora | `adb shell date` | Correcta; no se muestra en pantalla si se puede evitar |

Si el dispositivo no es exactamente el confirmado, se anota en la evidencia y se decide si
la toma sigue siendo válida. No se inventa el modelo en la narración.

### 2.3 Android Studio y Device Mirroring

1. Abrir Android Studio con el proyecto del worktree.
2. Conectar el Pixel por USB y aceptar la depuración.
3. Abrir **Device Explorer / Running Devices** y lanzar **Mirroring**.
4. Comprobar que la ventana de dispositivo responde al ratón y al teclado.
5. Cerrar paneles con rutas, logs o credenciales; dejar solo la ventana de dispositivo y,
   si se quiere, el árbol de archivos sin rutas personales.
6. Probar 30 s de espejo con scroll y un diálogo para detectar frames perdidos.

Si Device Mirroring no arranca o pierde frames, se aplica el fallback de captura de §7. No
se graba con tirones: se cambia de método antes de empezar.

### 2.4 Escritorio y privacidad

- Cerrar correo, mensajería, chats y pestañas con datos personales.
- Silenciar el sistema y el teléfono; desactivar actualizaciones automáticas y salvapantallas.
- Limpiar el historial de la terminal o abrir una pestaña nueva sin comandos anteriores.
- No dejar abierto el archivo local de propiedades ni paneles de entorno.
- Comprobar que no hay rutas personales, seriales ni nombres de usuario en la captura.

---

## 3. Seguridad de la key

La key de IA vive en el **archivo local de propiedades del proyecto** (ignorado por git) o
en una **variable de entorno**. Nunca se versiona, nunca se pega en el guion, nunca se
muestra en pantalla y nunca se comparte con el chat.

Reglas operativas:

1. **No abrir** el archivo local de propiedades durante la grabación.
2. **No ejecutar** `env`, `printenv`, `cat` ni `print` sobre ese archivo o la variable.
3. **No usar** `--info` ni `--debug` en Gradle durante la toma; no hacen falta.
4. **No pegar** la key en Gemini, OpenCode, VS Code, issues, commits ni capturas.
5. **No compartir** el APK de debug: el build real puede contener la key en `BuildConfig`.
6. Si la key aparece en un fotograma, en un log o en un clip: **detener la grabación**,
   borrar el clip, **rotar la key** y volver a grabar. No se publica “casi tapada”.
7. Al terminar, revisar los clips fotograma a fotograma en busca de la key, del archivo local
   o de una terminal con variables.
8. Si se sospecha una filtración, rotar la key es obligatorio; no basta con borrar el vídeo.

El comando de build no incluye la key: la toma del archivo local o del entorno la resuelve
Gradle. Si el build real falla por key ausente, se detiene la grabación y se configura fuera
de cámara; no se muestra el valor.

---

## 4. Build real e instalación

### 4.1 Build e instalación

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :composeApp:installDebug -Pplaybook.ai.mode=real
adb shell am start -n com.playbook.app/.MainActivity
```

- `-Pplaybook.ai.mode=real` es obligatorio para la demo. No se omite ni se cambia a `fake`.
- Con varios dispositivos, elegir uno con `ANDROID_SERIAL` (no mostrar el serial en pantalla).
- `installDebug` puede tardar; se hace antes de la toma, no durante.
- Si el build falla por key, red o configuración, se corrige fuera de cámara y se repite.
- No usar `init.sh` para instalar: su gate es fake. El gate ya se ejecutó en el pre-flight.

### 4.2 Arranque limpio

Antes del ensayo y antes de la toma principal, dejar la app sin Proyecto ni Notas:

```bash
adb shell pm clear com.playbook.app
adb shell am start -n com.playbook.app/.MainActivity
```

`pm clear` es **destructivo**: borra los datos locales de la app. Solo se ejecuta antes de
grabar, nunca durante una toma que ya tiene datos. Si se quiere conservar una toma, no se
limpia hasta haber exportado el clip.

### 4.3 Verificación de modo real

- La app debe arrancar con el onboarding, no con datos de una sesión anterior.
- En el log de arranque no debe aparecer un aviso de modo fake. No se muestra el log en
  pantalla; se consulta fuera de cámara.
- El smoke completo de §5 es la prueba fuerte de que el build real tiene key y modelos
  disponibles. Si falla solo `transcribe`, la cobertura de `tts`, `image` y `jev` pasa al
  pre-flight manual obligatorio de §5.

---

## 5. Smoke real de los modelos

Se ejecuta **fuera del gate**, con key disponible y sin mostrar la salida cruda en pantalla.
El test imprime solo dimensiones, longitudes y conteos; no imprime la key, el prompt, el
contexto ni el contenido.

```bash
PLAYBOOK_AI_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest \
  --tests "com.playbook.app.ai.RealAiSmokeTest" \
  -Pplaybook.ai.mode=real -Pplaybook.ai.smoke=1 --rerun-tasks
```

Qué cubre de verdad:

| Modelo | Uso en la demo | Cobertura del smoke |
|---|---|---|
| chat | Brainstorming y Q&A | `smokeAllSixModels` y `smokeRealChat` |
| embedding | Indexado y relacionadas | `smokeAllSixModels` y `smokeRealChat` (indexado/RAG) |
| jev | Sugerencia de clasificación | Solo `smokeAllSixModels`; va **después** de `transcribe` |
| tts | Leer en voz alta | Solo `smokeAllSixModels`; va **antes** de `transcribe` |
| image | Imagen generada | Solo `smokeAllSixModels`; va **después** de `transcribe` |
| transcribe | **No se usa en la demo** | `smokeAllSixModels`; no bloquea la demo si falla, pero corta el test |

`smokeAllSixModels` es **todo-o-nada**: ejecuta embedding, chat, tts, `transcribe`, image y
jev **en ese orden**. Si `transcribe` falla, el test aborta y **`image` y `jev` no llegan a
ejecutarse**. `smokeRealChat` solo cubre chat e indexado/RAG: **no** cubre `tts`, `image` ni
`jev`, así que no aísla el camino completo de la demo.

**Criterio de luz verde:**

1. El **smoke completo** termina en `BUILD SUCCESSFUL` y la salida sanitizada muestra los
   conteos de los seis modelos. Es la única vía que cubre de una vez los cinco modelos que usa
   la demo.
2. Si el smoke completo falla **solo** en `transcribe` (el error es de `transcribe` y no de
   otro modelo), se documenta el fallo y se hace un **pre-flight manual obligatorio** sobre
   una instalación de pruebas, con la misma app y el mismo build real:
   - `tts`: leer en voz alta una respuesta del Q&A y comprobar que suena audio real.
   - `image`: generar una imagen con “Generar con Playbook” y comprobar el preview.
   - `jev`: pedir una sugerencia de clasificación en Nueva nota y comprobar que propone Track
     o Géneros.
   Registrar la evidencia **sanitizada** (sin key, prompt, contexto ni contenido de usuario) en
   `composeApp/build/smoke-evidence/video-script-mvp-ai/`.
3. Si falla cualquier otro modelo, o si el pre-flight manual de `tts`, `image` o `jev` falla,
   **no se graba**.

**No usar `smokeRealChat` como sustituto del smoke completo.** Solo sirve para diagnosticar
chat e indexado/RAG; no demuestra `tts`, `image` ni `jev`.

Tras el pre-flight manual, ejecutar `adb shell pm clear com.playbook.app` y arrancar de nuevo
para empezar la toma limpia. El `pm clear` es destructivo y se hace **antes** de grabar, nunca
durante.

Guardar la salida sanitizada en una carpeta ignorada por git, por ejemplo
`composeApp/build/smoke-evidence/video-script-mvp-ai/`. No versionar esa salida. Antes de
guardarla, comprobar que no contiene key, prompt, contexto ni contenido de usuario.

---

## 6. Datos en vivo y ensayo

### 6.1 Regla de datos

- Se parte de una instalación limpia: **sin Proyecto y sin Notas precargadas**.
- El Proyecto se crea en el onboarding durante la toma.
- Las Notas se crean en la toma con el editor y con la imagen generada.
- Las sugerencias, la clasificación, la respuesta del Q&A, las fuentes y la imagen las
  produce la IA real. El guion usa placeholders `«…»`; no se rellenan antes.
- Si una salida no sirve, se repite la acción o se repite la toma. No se edita el texto
  dentro de la app para que parezca otra cosa.

### 6.2 Ensayo en tres pasadas

| Pasada | Objetivo | Qué se registra |
|---|---|---|
| 1. Lectura | Ajustar la narración al ritmo y a los 13:30 | Cronómetro por bloque; palabras que sobran |
| 2. Ensayo real | Recorrer toda la demo con IA real y grabar los respaldos B01–B07 | Latencia real, fallos, clips de respaldo |
| 3. Toma principal | Grabar con captura, micrófono y datos limpios | Tomas C01–C16 y tiempos reales |

En la pasada 1 se lee el guion en voz alta con cronómetro. Si un bloque se pasa, se aplica
la tabla de recortes del [guion §7](guion-demo-mvp.md#7-tabla-de-recortes); no se acelera la
voz. En la pasada 2 se graban los respaldos reales: son la red de seguridad de la toma
principal. En la pasada 3 no se improvisa la estructura: se sigue el storyboard.

### 6.3 Qué se puede repetir y qué no

- Se puede repetir una superficie, un plano o una frase.
- No se puede mezclar una toma con datos de otra sesión sin advertirlo.
- No se puede usar un clip de una versión anterior de la app si la UI cambió.
- No se puede sustituir una respuesta de la IA por un texto escrito a mano.
- No se puede cambiar a fake “solo para terminar”.

---

## 7. Captura y audio

### 7.1 Captura principal

1. Abrir la ventana de dispositivo de Android Studio en vertical.
2. Ajustar el zoom para que la app ocupe el alto y se lean los textos.
3. Grabar la ventana con QuickTime Player (incluido en macOS) o con la herramienta de
   captura del sistema, a 1920×1080 y 30 fps como propuesta.
4. Grabar en segmentos por bloque o por superficie; así un fallo no invalida toda la toma.
5. Detener la captura antes de cambiar de dispositivo o de limpiar datos.

**Reglas de plano:** nada de barras del IDE con rutas; nada de notificaciones; nada de
ratón tembloroso; los clics se hacen con calma y se dejan ver.

### 7.2 Fallback de captura

Si Device Mirroring no arranca, pierde frames o no permite grabar:

- **Grabadora del Pixel:** grabar la pantalla desde el panel de control del dispositivo y
  pasar el archivo por USB o por la nube local.
- **`adb screenrecord` por tramos:** máximos de 3 minutos por archivo, para no perder todo
  el bloque si falla.

```bash
mkdir -p clips
adb shell screenrecord --time-limit 180 --bit-rate 8000000 /sdcard/pb-01.mp4
adb pull /sdcard/pb-01.mp4 clips/pb-01.mp4
adb shell rm /sdcard/pb-01.mp4
```

- Repetir por tramos: onboarding, Brainstorming, Nota, GDD, Q&A, relacionadas e imagen.
- Comprobar la orientación y el audio al reproducir el primer tramo.
- El guion y el storyboard no cambian: solo cambia el método de captura.

### 7.3 Audio

- Micrófono externo o el del equipo, a 48 kHz, con niveles comprobados.
- Grabar 20 s de silencio de sala para limpiar el ruido.
- Hacer una palmada al inicio de cada bloque para sincronizar audio y vídeo.
- Voz cercana, sin música y sin efeitos.
- Durante la lectura en voz alta del Q&A, bajar la voz y dejar oír 2–3 s del audio real.
- Revisar que no haya eco, ventilador, teclado ni notificaciones.

---

## 8. Orden de clips

### 8.1 Orden de rodaje recomendado

1. **Demo primero, en una sola sesión:** C06 → C12 (P05–P11), con datos limpios y sin
   reinstalar entre superficies. Es la parte que depende del estado y de la IA real.
2. **Estáticos después:** C01 → C05 (P00–P04) y C13 → C15 (P12–P14). No tocan el estado de
   la app.
3. **Respaldos:** B01–B07 se graban en el ensayo de la pasada 2, no durante la toma
   principal, para no ensuciar el estado.
4. **Margen:** C16 (P15) solo si hace falta; no se publica como contenido.

### 8.2 Tabla de clips

| Clip | Plano | Contenido | Duración | Orden de rodaje | Respaldo |
|---|---|---|---:|---:|---|
| C01 | P00 | Portada | 0:40 | 2 | — |
| C02 | P01 | Producto y tesis | 1:20 | 2 | — |
| C03 | P02 | Arquitectura | 1:20 | 2 | — |
| C04 | P03 | Harness / SDD | 1:30 | 2 | — |
| C05 | P04 | Herramientas | 1:00 | 2 | — |
| C06 | P05 | Onboarding / Proyecto | 0:45 | 1 | B01 |
| C07 | P06 | Brainstorming y Guardar | 0:50 | 1 | B02 |
| C08 | P07 | Nueva Nota | 0:55 | 1 | B03 |
| C09 | P08 | GDD híbrido | 0:35 | 1 | B04 |
| C10 | P09 | Q&A | 1:15 | 1 | B05 |
| C11 | P10 | Relacionadas | 0:35 | 1 | B06 |
| C12 | P11 | Imagen | 0:35 | 1 | B07 |
| C13 | P12 | Resultados e historia | 1:00 | 2 | — |
| C14 | P13 | Lecciones y futuro | 1:10 | 2 | — |
| C15 | P14 | Cierre | 0:40 | 2 | — |
| C16 | P15 | Margen | 0:50 | 3 | — |

**Suma de contenido (C01–C14):** 13:30. **Con cierre (C01–C15):** 14:10. **Con margen:** 15:00.
El orden de edición es C01 → C16; el de rodaje es el de la columna “orden de rodaje”.

### 8.3 Nombres de archivo

Usar un patrón estable y sin datos personales:

```
clips/C01-portada.mov
clips/C06-onboarding.mov
clips/B05-qa.mov
```

Los clips se guardan fuera del repo o en una carpeta ignorada; no se commitean. El nombre
final del vídeo se decide al exportar (§11).

---

## 9. Checklist

### 9.1 Antes de grabar

- [ ] `pwd` en el worktree de la feature.
- [ ] JDK 21, SDK y `adb` disponibles.
- [ ] Gate `CI=true ./init.sh` en verde.
- [ ] Pixel 8 Pro con Android 17 conectado y autorizado.
- [ ] Device Mirroring probado durante 30 s.
- [ ] Vertical bloqueado; `es-ES`; font scale 1.0.
- [ ] “No molestar” activo; notificaciones y actualizaciones silenciadas.
- [ ] Batería > 80 % o enchufado; ≥ 5 GB libres.
- [ ] Build real instalado con `-Pplaybook.ai.mode=real`.
- [ ] Arranque limpio: sin Proyecto ni Notas.
- [ ] Smoke completo de los seis modelos en verde.
- [ ] Si el smoke falla solo en `transcribe`: pre-flight manual de `tts`, `image` y `jev`
      registrado (evidencia sanitizada) y `adb shell pm clear com.playbook.app` ejecutado.
- [ ] Par de Notas de “Ver relacionadas” comprobado en el ensayo (similitud ≥ 0.8) o B06 real
      preparado; no se presenta un estado vacío como si tuviera enlaces.
- [ ] Panel “Dictado” de Nueva nota fuera de cuadro y sin interactuar; B03 disponible.
- [ ] Key fuera de cámara; archivo local cerrado; terminal limpia.
- [ ] Respaldos B01–B07 grabados en el ensayo.
- [ ] Micrófono, niveles y silencio de sala comprobados.
- [ ] Cronómetro y tabla de tiempos a la vista.
- [ ] Storyboard y guion abiertos en otra pantalla, no en la captura.

### 9.2 Durante la grabación

- [ ] Grabar por bloques; no dejar una sola toma de 14 minutos sin puntos de corte.
- [ ] Palmada de sincronía al inicio de cada bloque.
- [ ] En Brainstorming, pulsar “Guardar” tras la sugerencia de Género y comprobar que la ficha
      persiste Título y Géneros y vuelve a la lista; Cancelar no escribe.
- [ ] Leer la narración literal; sustituir solo los placeholders `«…»` por lo que se ve.
- [ ] Esperar el resultado real; si tarda, jump-cut al resultado, no a una maqueta.
- [ ] No mostrar la key, el archivo local, variables de entorno ni logs crudos.
- [ ] No mostrar dictado ni transcripción.
- [ ] No cambiar a fake.
- [ ] Anotar el tiempo real de cada bloque y cualquier desviación.

### 9.3 Después de grabar

- [ ] Parar la captura y el micrófono.
- [ ] Revisar fotograma a fotograma los clips con terminal, IDE y ajustes.
- [ ] Confirmar que no aparece la key, el serial, rutas personales ni notificaciones.
- [ ] Comprobar la duración: 13:30 de contenido, 14:10 con cierre, ≤ 15:00.
- [ ] Revisar audio: voz clara, sin música, sin clipping.
- [ ] Exportar el máster y comprobar el archivo final.
- [ ] Subir a WeTransfer y probar el enlace en una ventana privada.
- [ ] Limpiar datos, clips y archivos temporales (§12).
- [ ] Revisar `git status`; no commitear capturas ni secretos.

---

## 10. Fallback real

| Fallo | Qué se hace | Qué no se hace |
|---|---|---|
| Device Mirroring no arranca | Grabadora del Pixel o `adb screenrecord` por tramos | Grabar con tirones o con una maqueta |
| Se pierden frames | Cambiar a grabación en el dispositivo | Publicar el clip con saltos |
| Falla la red o el proveedor | Detener la toma y reprogramar | Cambiar a fake o inventar la respuesta |
| Falla el chat | Repetir; si persiste, usar B02/B05 del mismo build | Escribir la respuesta a mano |
| Falla el embedding o el indexado | Esperar, reindexar o repetir; si persiste, usar B06 | Mostrar `INDEXED` sin estarlo |
| Falla tts | Repetir o usar B05 con audio real | Simular el audio |
| Falla image | Repetir o usar B07 con imagen real | Usar un asset del repo como si fuera generado |
| Entra una notificación | Cortar el segmento y repetirlo | Taparla en edición y fingir que no pasó |
| El dispositivo rota | Bloquear y repetir el segmento | Recortar el plano |
| Aparece la key | Parar, borrar el clip, rotar la key y repetir | Publicar “casi tapada” |
| El ensayo se pasa de 15:00 | Aplicar los recortes del guion | Acelerar la voz o cortar palabras |

**Regla de respaldo:** un clip de respaldo solo vale si es de la misma app, el mismo build
real y una superficie equivalente. Si no hay respaldo real, se reprograma.

---

## 11. Publicación en WeTransfer

1. **Exportar** el máster a 1920×1080, 30 fps, H.264 y audio de voz; sin música.
2. **Comprobar** duración: objetivo 14:10, límite 15:00. Si supera 15:00, no se publica.
3. **Nombrar** el archivo con un patrón estable y sin datos personales. Propuesta:
   `playbook-demo-mvp-2026-10-22.mp4` (pendiente de confirmación).
4. **Subir** a WeTransfer con el enlace configurado para la persona evaluadora.
5. **Probar** el enlace en una ventana privada y comprobar que el archivo se reproduce.
6. **No subir** el vídeo al repositorio, ni clips, ni capturas pesadas, ni el APK.
7. **No incluir** música: la pieza es voz y pantalla. Si se añade música, hay que confirmar
   licencia y decisión del autor; por ahora está descartada.
8. **No incluir** la key, el archivo local, logs ni metadatos con rutas personales. Revisar
   los metadatos del export si el editor los conserva.

El enlace de WeTransfer no se versiona en el repo. Se comparte por el canal que indique el
autor y se anota la fecha de expiración.

---

## 12. Limpieza

```bash
adb shell pm clear com.playbook.app
adb uninstall com.playbook.app
rm -rf clips/
```

- `pm clear` y `uninstall` borran los datos demo del dispositivo; no afectan al repo.
- Borrar clips, exports intermedios, capturas y archivos de trabajo.
- Borrar la salida del smoke si está fuera de `build/`; si está dentro de `build/`, queda
  ignorada por git, pero se revisa igualmente.
- Revisar que no haya capturas ni vídeos en `docs/`, `composeApp/` ni la raíz.
- Ejecutar `git status --short` y `git diff --check`; no debe haber binarios, secretos ni
  cambios fuera de alcance.
- Revisar el historial de la terminal: no debe contener la key.
- Si la key se filtró, rotarla y registrar el incidente; no basta con borrar el archivo.
- Dejar el worktree listo para que otra sesión pueda ejecutar `CI=true ./init.sh`.

---

## 13. Ensayo y ajuste de duración

### 13.1 Ensayo de mesa

- Leer la narración completa en voz alta con cronómetro.
- Medir cada bloque y anotar el desvío.
- Si el total de contenido supera 13:30, aplicar los recortes en orden:
  relacionadas → resultados → futuro → arquitectura → esperas del demo → producto → harness.
- Si el total baja de 13:30, no rellenar con muletillas: alargar una explicación útil o
  dejar respirar la demo.
- Objetivo: 13:30 de contenido y 14:10 con cierre. El límite de 15:00 no es un objetivo.

### 13.2 Ensayo técnico

- Recorrer la demo completa con IA real.
- Si el smoke completo falla solo en `transcribe`, ejecutar el pre-flight manual de `tts`,
  `image` y `jev` de §5 antes de dar el ensayo por bueno.
- Medir la latencia de chat, embedding, jev, tts e image.
- Grabar B01–B07 y comprobar que se ven bien.
- Probar el fallback de captura una vez.
- Revisar el audio y la sincronización.
- Anotar los tiempos reales y ajustar el storyboard si cambia un plano.

### 13.3 Toma final

- Limpiar datos y repetir el arranque limpio.
- Grabar la demo en una sesión continua.
- Grabar los estáticos y el cierre.
- Revisar los clips antes de desmontar el equipo.
- Exportar, revisar y publicar.

### 13.4 Confirmaciones antes del ensayo técnico

- [ ] Nombre final del archivo.
- [ ] Resolución maestra (propuesta 1920×1080).
- [ ] Si Gemini se muestra en pantalla o solo se nombra.
- [ ] Estilo de lower-thirds y si se usan los seis propuestos.
- [ ] Si el cierre lleva tarjeta de enlaces o solo icono y frase.

---

## 14. Riesgos asumidos de la demo

Estos tres puntos **no se reparan** en esta entrega: se asumen y se controlan en el ensayo y
la toma. No cambian el alcance, el código ni la duración.

### 14.1 D · “Ver relacionadas” depende de un umbral

- El enlace aparece solo si la similitud entre embeddings es **≥ 0.8**
  (`SemanticLinkingService.DEFAULT_SIMILARITY_THRESHOLD`). Reindexar recalcula el embedding,
  pero **no garantiza** que el par de Notas supere el umbral.
- **Regla de ensayo:** comprobar el par de Notas que se va a mostrar. Si no supera 0.8,
  reformular el texto **en el ensayo** (nunca en la toma) o usar B06 real. No se presenta un
  estado vacío como si tuviera enlaces.
- Referencia: [guion §4.5.6](guion-demo-mvp.md#456-indexado-y-notas-relacionadas--10101045)
  y [storyboard P10](storyboard-demo-mvp.md#p10--demo--relacionadas--10101045).

### 14.2 E · Panel “Dictado” en Nueva nota

- En Nueva nota se renderiza el panel **“Dictado”**. No se usa en la demo: mantenerlo **fuera
  de cuadro** y **no interactuar** con él.
- Si entra en el plano, **repetir la toma** o usar B03. No se usa ni se simula.
- Referencia: [guion §4.5.3](guion-demo-mvp.md#453-nueva-nota-con-sugerencias-y-guardado--725820)
  y [storyboard P07](storyboard-demo-mvp.md#p07--demo--nueva-nota--725820).

### 14.3 F · Fake del gate frente a smoke real

- El **gate** (`CI=true ./init.sh`) y **los tests automáticos del gate** usan IA **fake**:
  determinista, sin red y sin key.
- El **smoke real** (`RealAiSmokeTest`) es **manual y key-gated**: no forma parte del gate, no
  se ejecuta en CI y no se presenta como test automático.
- Por eso el guion no dice “los tests usan siempre fake” sin esa precisión. Referencia:
  [guion §4.3](guion-demo-mvp.md#43-bloque-3--arquitectura--200320) y [guion §9](guion-demo-mvp.md#9-mapa-claim--fuente).

---

## 15. Enlaces

- [Guion del vídeo](guion-demo-mvp.md)
- [Storyboard](storyboard-demo-mvp.md)
- [README](../../README.md) · [ARCHITECTURE](../../ARCHITECTURE.md) · [PROGRESS](../../PROGRESS.md)
- [AGENTS.md](../../AGENTS.md) · [CONSTRAINTS.md](../../CONSTRAINTS.md)
- [Plan de ejecución del MVP](../mvp-execution-plan.md)
- [ADRs](../adr/) · [specs](../specs/) · [validaciones](../validations/)
- [Release Android](../release/android.md) · [iOS/TestFlight](../release/ios-testflight.md)
- [Riesgos y preguntas abiertas](../risks-and-open-questions.md)
- [`RealAiSmokeTest.kt`](../../composeApp/src/androidUnitTest/kotlin/com/playbook/app/ai/RealAiSmokeTest.kt)
