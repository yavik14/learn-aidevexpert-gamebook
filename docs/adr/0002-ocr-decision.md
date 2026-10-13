# ADR 0002: OCR del boceto a texto derivado (viabilidad)

- **Status:** accepted (validación independiente `accept`, 2026-10-13).
- **Fecha:** 2026-10-13
- **Feature:** `image-ocr-optional`
- **Decisores:** implementer de `image-ocr-optional` (propuesta).
- **Relacionado:** `docs/adr/0001-ai-runtime-decision.md`,
  `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`,
  `docs/domain-model.md`, `docs/build-brief.md`, `CONTEXT.md`, `ARCHITECTURE.md`
  (sección "Deferred").

## Contexto

Playbook captura notas de texto, voz e imagen y construye un GDD vivo
(local-first, single-user, Android + iOS en paralelo con KMP + Compose
Multiplatform). La feature previa `image-capture-camera` (`accepted`) ya permite
adjuntar un **boceto** (cámara/galería) a una Nota; el `Adjunto` guarda
metadatos + ruta relativa a un archivo de imagen, **no** el texto derivado.

`image-ocr-optional` es una feature **opcional por diseño**: `CONTEXT.md`
distingue el `Adjunto` del **texto derivado** (transcripción de voz / OCR del
boceto), que vive en la Nota; `docs/domain-model.md` deja abierto si un
`Adjunto` sin texto es indexable para RAG; `docs/build-brief.md` **no** incluye
el OCR como requisito del MVP. La pregunta a cerrar es de **viabilidad**: ¿conviene
extraer el **texto manuscrito** de un boceto y guardarlo como texto derivado de la
Nota en este proyecto (KMP, Android + iOS, offline-first, sin dependencias pesadas
sin justificación)?

Restricciones relevantes del producto y del repo:

- **Objetivo de contenido:** el boceto de un GDD suele llevar **anotaciones
  manuscritas** (flechas, bocadillos, listas a mano), no texto impreso.
- **Offline-first:** la captura nunca se bloquea; el texto derivado sería
  asíncrono y tolerante a fallos.
- **Sin dependencias pesadas sin justificación** y **nunca versionar secrets**
  (política durable de `ai-runtime-decision`).
- **Código compartido real en KMP** es criterio de éxito; se evita duplicar lógica
  por plataforma.
- El puerto `AiClient` existe (`ai-client-interface`, `accepted`) pero es
  **text-only**: `embed(texts): List<Embedding>` y `generate(request): String`.
- El repo **no puede** ejecutar red ni modelos nativos en el gate (`init.sh` es no
  bloqueante, sin dev servers ni simuladores).

## Criterios de decisión

1. **Alineación con el objetivo real:** texto **manuscrito** del boceto, no
   impreso.
2. **Código compartido KMP** vs superficie nativa por plataforma.
3. **Peso de dependencias** (tamaño de app/CI) y **secrets/costo**.
4. **Offline-first** y no bloquear la captura.
5. **Encaje con la arquitectura existente** (puerto `AiClient`/patrón
   `VoiceTranscriber`, vendor-neutral, sin romper contratos aceptados).
6. **Valor de producto hoy** vs esfuerzo (la feature es opcional/min).
7. **Testabilidad/determinismo** con el harness actual (sin red ni nativos en el
   gate).

## Alternativas consideradas

### A. Android ML Kit Text Recognition v2 (`com.google.mlkit:text-recognition*`)

Reconocimiento on-device en `androidMain` con el modelo *bundled* o vía Google
Play Services.

| Criterio | Evaluación |
| --- | --- |
| Objetivo (manuscrito) | **Bajo.** Orientado a **texto impreso**; la calidad sobre manuscrito no está garantizada. |
| Código compartido KMP | **Bajo.** Dependencia Android-only; sólo `androidMain`, sin superficie compartida. |
| Peso/secrets | Peso **Medio** (modelo *bundled* de varios MB o dependencia de Play Services); sin secrets con *bundled*. |
| Offline-first | **Alto** con *bundled* (offline), **Medio** con Play Services (puede requerir descarga). |
| Encaje arquitectónico | **Bajo/Medio.** Rompería la simetría con iOS y no encaja en `AiClient` (text-only). |
| Valor/esfuerzo | **Bajo.** Dep pesada por una capacidad de calidad incierta sobre mano. |

Se descarta: dependencia pesada por plataforma, calidad incierta sobre manuscrito
y poca superficie compartida.

### B. iOS Vision `VNRecognizeTextRequest`

Framework del sistema en `iosMain` (iOS 15.3+ disponible en el target), sin
dependencia externa.

| Criterio | Evaluación |
| --- | --- |
| Objetivo (manuscrito) | **Bajo/Medio.** Primario impreso; el manuscrito no está garantizado. |
| Código compartido KMP | **Bajo.** Implementación iOS-only. |
| Peso/secrets | **Alto.** Sin dependencia externa ni secrets. |
| Offline-first | **Alto.** On-device. |
| Encaje arquitectónico | **Bajo.** Asimétrico frente a Android (que exigiría ML Kit). |
| Valor/esfuerzo | **Bajo.** Dos implementaciones nativas distintas por una capacidad incierta. |

Se descarta como integración MVP: crearía una **asimetría** Android/iOS (en
Android haría falta una dep pesada) y la calidad sobre manuscrito no está
garantizada.

### C. `AiClient` (puerto existente) con capacidad de visión

Reutilizar el puerto `AiClient` para mandar la imagen a un proveedor con visión.

| Criterio | Evaluación |
| --- | --- |
| Objetivo (manuscrito) | **Variable/Medio.** Modelos multimodales pueden leer impreso y algo de manuscrito, pero **no** está medido aquí. |
| Código compartido KMP | **Alto** (si el puerto aceptara imágenes). |
| Peso/secrets | Cloud: requiere key/costo/red (**Secrets Sí**); rompe local-first. |
| Offline-first | **Bajo** (red). Mitigable con pendiente + reintento. |
| Encaje arquitectónico | **No viable hoy:** el contrato es **text-only**; extenderlo a visión cambia un contrato aceptado y su neutralidad de vendor. |
| Valor/esfuerzo | **Bajo** (fuera de alcance). |

No viable en esta feature: `embed`/`generate` no aceptan imágenes; extender el
puerto a visión es una decisión de arquitectura aparte (vendor-neutral) y está
fuera de alcance.

### D. OCR cloud (API externa dedicada)

Llamar a un servicio de OCR por red con una API key.

| Criterio | Evaluación |
| --- | --- |
| Objetivo (manuscrito) | **Medio/Variable.** Depende del proveedor. |
| Código compartido KMP | **Medio.** Adaptador KMP + serialización. |
| Peso/secrets | **Secrets Sí** + costo; **viola** la política durable de no versionar keys. |
| Offline-first | **Bajo.** Requiere red. |
| Encaje arquitectónico | **Bajo.** Introduce un proveedor y keys fuera del puerto. |
| Valor/esfuerzo | **Bajo.** No hay backend; la gestión de keys en local-first no es trivial. |

Se descarta: viola offline-first y la política de secrets del ADR 0001.

### E. Descartar para el MVP

No integrar OCR; documentar la decisión y fijar disparadores de re-evaluación.

| Criterio | Evaluación |
| --- | --- |
| Objetivo (manuscrito) | N/A hoy; se difiere hasta que exista un motor con calidad y encaje aceptables. |
| Código compartido KMP | **Alto.** Sin código nuevo. |
| Peso/secrets | **Alto.** Sin dependencias ni secrets. |
| Offline-first | **Alto.** Nada que bloquee ni que falle. |
| Encaje arquitectónico | **Alto.** No toca `AiClient` ni `:core`; deja abierto un seam futuro (`TextRecognizer`). |
| Valor/esfuerzo | **Alto.** Evita prometer una capacidad de bajo valor y evitar deps pesadas sin justificación. |

**Elegida:** ver decisión.

## Decisión

**Se descarta la integración de OCR para el MVP** (alternativa E). Fundamento:

1. **El objetivo es texto manuscrito.** Los motores on-device de bajo costo
   (ML Kit Text Recognition; iOS Vision) están orientados a **texto impreso**; la
   calidad sobre **manuscrito** no es confiable, así que el valor de producto hoy
   es bajo.
2. **Asimetría y peso de dependencias.** iOS aportaría OCR sin dependencia, pero
   Android exigiría una dependencia nativa pesada (ML Kit) con **poca superficie
   compartida KMP** y costo de tamaño/CI. Viola "sin dependencias pesadas sin
   justificación".
3. **`AiClient` no puede hacer visión** sin extender el puerto (hoy text-only y
   vendor-neutral por diseño). Un proveedor de visión real es follow-up del ADR
   0001 y no existe adaptador real todavía (el default es `FakeAiClient`).
4. **La feature es explícitamente opcional/min** y el OCR **no** es requisito del
   MVP (`docs/build-brief.md`): es un "nice to have".
5. **Offline-first.** El OCR sería texto derivado asíncrono que no debe bloquear
   la captura y no aporta al flujo end-to-end mínimo.

Si en el futuro se implementa, el patrón natural es un **puerto
`TextRecognizer` + adaptadores por plataforma** (análogo a `VoiceTranscriber`),
consumiendo la imagen ya capturada por `image-capture-camera` y persistiendo el
texto derivado en la Nota (no en el `Adjunto`). **Hoy no se crea ese seam.**

## Consecuencias

**Positivas**

- No se cargan dependencias pesadas ni secrets; la app y el gate siguen livianos.
- No se promete una capacidad de bajo valor sobre manuscrito.
- El puerto `AiClient` y `:core` no cambian; los contratos aceptados se preservan.
- La puerta queda abierta a un seam `TextRecognizer` cuando aparezca un motor con
  calidad/encaje aceptables.

**Negativas / costos**

- El boceto **no** aporta texto indexable: para RAG, un `Adjunto` de imagen sin
  texto no participa (la pregunta sigue abierta para la feature de indexado/RAG).
- Si aparece una necesidad real de texto derivado, habrá que implementar OCR
  después (con el costo completo que hoy se evita).

**Neutras**

- No hay cambios de producto, esquema (`Schema.version == 4`), dependencias ni
  UI. `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` y
  `ARCHITECTURE.md` (sección "Deferred") registran el descarte.

## Disparadores de re-evaluación

Reabrir `image-ocr-optional` (y revisar este ADR) si ocurre **cualquiera** de:

- Existe un adaptador **`AiClient` con capacidad de visión** (cloud con key
  provista por el usuario, o fase 2 on-device) por el mismo puerto.
- El producto **necesita** texto derivado de `Adjunto` para RAG
  (`docs/risks-and-open-questions.md`, "Blocking Next Phase").
- Aparece **evidencia de calidad** aceptable de OCR sobre **manuscrito** en una
  plataforma objetivo (idealmente sin dependencia pesada).

## Evidencia

- **No se ejecutó OCR ni se midió calidad.** Esta es una decisión de viabilidad
  basada en la evaluación de alternativas y en las restricciones del repo; no en
  mediciones propias (no había seam, ni motor, ni imágenes de muestra etiquetadas
  en el repo).
- Gate de verificación de que la decisión es honesta y sin efectos de producto:
  `./init.sh` → exit 0; `:core:testDebugUnitTest`,
  `:core:iosSimulatorArm64Test`, `:composeApp:assembleDebug` y
  `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD
  SUCCESSFUL con los tests existentes en verde, **sin** cambios de producto,
  dependencias ni esquema (`Schema.version == 4`, sin `.sqm` nuevo). Detalle en el
  arreglo `evidence` de `image-ocr-optional` en `feature_list.json`.
- **Control negativo:** `grep -riE "mlkit|VNRecognizeTextRequest|TextRecognizer"
  core/src composeApp/src` → sin resultados (no existe ningún artefacto de OCR).

## Límites honestos

- La calidad de ML Kit / iOS Vision / proveedores cloud sobre el **manuscrito** de
  este producto **no fue medida**; se infiere de su orientación documentada a
  texto impreso y se declara como incertidumbre, no como medición.
- Las estimaciones de peso/tamaño de ML Kit y de calidad no traen URLs fechadas
  concretas; son orientativas y deben re-verificarse si se aplica un disparador.
- La política de secrets del ADR 0001 se mantiene: ninguna ruta de OCR cloud con
  key se adopta aquí.

## Preguntas abiertas

- **RAG de adjuntos sin texto:** el descarte de OCR implica que un boceto no
  aporta texto indexable; definir si un `Adjunto` sin texto participa del RAG
  queda para la feature de indexado/RAG (sigue en
  `docs/risks-and-open-questions.md`).
- **`AiClient` con visión:** extender el puerto para aceptar imágenes es una
  decisión de arquitectura aparte (vendor-neutral) y no se aborda aquí.
- **Calidad de manuscrito:** si en el futuro hay evidencia de calidad aceptable
  sin dependencia pesada, aplicar los disparadores y reabrir la feature con un
  seam `TextRecognizer` (patrón `VoiceTranscriber`).
