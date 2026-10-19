# Constraints

Reglas durables (MUST / MUST NOT) que las próximas features deben respetar. No
duplican la spec ni `ARCHITECTURE.md`; registran límites operativos descubiertos.

## Release y credenciales

- **MUST NOT** commitear credenciales ni artefactos de firma/release: `.p8`,
  `.p12`, `.cer`, `.mobileprovision`, `AuthKey_*`, `.ipa`, `.xcarchive`,
  `ExportOptions.plist` renderizado, `iosApp/Configuration/Local.xcconfig`,
  `*.apiKey`. Están en `.gitignore`; el runbook
  `docs/release/ios-testflight.md` documenta el flujo. Razón: `AGENTS.md` ya
  prohíbe secrets y un leak de firma compromete la cuenta.
- **MUST** mantener el bundle id de iOS estable (`com.playbook.app`, vía
  `${BUNDLE_ID}`) y el `TEAM_ID` fuera de los archivos versionados
  (`Local.xcconfig` gitignored o env). Razón: el bundle id se registra una vez en
  App Store Connect y no debe cambiar por team.
- **MUST** usar un `CURRENT_PROJECT_VERSION` (build number) nuevo en cada subida
  a TestFlight; reutilizarlo es rechazado por App Store Connect. El marketing
  version va en `MARKETING_VERSION`.
- La identity de firma de `Release` es `Apple Distribution` (`CODE_SIGN_STYLE =
  Automatic`); `Debug` conserva `Apple Development`.

## Gate y pipeline

- `./init.sh` **MUST NOT** archivar, exportar ni subir a TestFlight, ni levantar
  dev servers/simuladores/sesiones de larga duración. Sigue siendo el gate no
  bloqueante; el release se invoca explícitamente con
  `scripts/ios-release/*.sh`. Razón: el upload es lento, requiere red y
  credenciales account-gated.
- El scheme `iosApp` **MUST** estar compartido en
  `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` para que
  `xcodebuild archive`/`-exportArchive` por CLI sean deterministas. Razón: antes
  se autocreaba en user data (no versionado) y no era reproducible en CI/otro
  checkout.

## Indexado de IA

- El pipeline de indexado **MUST NOT** clasificar ni etiquetar la Nota ni
  bloquear la captura: un fallo de IA deja la Nota `PENDING` y nunca lanza hacia
  el llamador. Razón: la captura es local-first/tolerante a fallos. (La
  **sugerencia** de clasificación es una acción explícita y separada: ver
  "Géneros y clasificación asistida".)
- La transición `pendiente → fallida`, los reintentos, el scheduling y la
  detección de conectividad **MUST NOT** implementarse en el indexado: pertenecen
  a `offline-pending-retry`. El indexado sólo escribe `INDEXED`/`PENDING` (nunca
  `FAILED`). Aplica a features que dependen de ésta
  (`offline-pending-retry`, `note-enrichment-status-ui`).
- La **UI de estado de enriquecimiento** (`note-enrichment-status-ui`) **MUST
  NOT** reintentar automáticamente, agendar ni detectar conectividad: el
  re-indexado es **manual** (un tap del usuario) y los estados visibles provienen
  de `NoteStatus`, no de clasificación IA. El enriquecimiento **MUST NOT** pisar
  `track`/`tags`/`body`/`owner`/timestamps (la clasificación es del usuario; el
  re-indexado sólo llama `updateStatus`). Razón: la clasificación IA está
  descartada y el reintento/`FAILED` pertenece a `offline-pending-retry`.
- El estado `FAILED` **MUST NOT** escribirse en el indexado directo
  (`NoteIndexingService`/`NoteIndexingOrchestrator`): sólo `PendingIndexingRetry`
  lo escribe, y sólo tras agotar `budget` intentos fallidos (`index_attempts`,
  esquema v6). Razón: un fallo de IA no debe degradar la captura ni marcar
  irrecuperable una Nota sin presupuesto durable.
- Cuando `AiAvailability.isAvailable()` es `false`, el reintento **MUST** ser
  no-op: **MUST NOT** consumir presupuesto, cambiar estados ni llamar a
  `AiClient`. Razón: "IA no disponible" no es un fallo de la Nota; no debe contar
  para el presupuesto ni promover a `FAILED`.

## Observabilidad / diagnóstico

- `Diagnostics.recordFailure` **MUST** ser best-effort: **nunca** lanza ni bloquea
  al llamador; una implementación que falle al escribir (storage/IO) **MUST**
  tragarse el error. Razón: el diagnóstico nunca debe romper el flujo observado
  (captura local-first tolerante a fallos).
- El log de diagnóstico **MUST NOT** contener datos de contenido ni sensibles:
  `body`, preguntas del usuario, rutas de adjuntos, IDs ni secrets. Sólo se
  registran la fuente, un resumen **estable** y (si aplica) el mensaje de
  excepción recortado. Razón: privacidad; el archivo es legible en el dispositivo.
- La observabilidad **MUST NOT** usar telemetría remota, crash reporting ni
  librerías de logging en el MVP; el sink es local y se limita a **fallos** (no
  caminos de éxito). Razón: MVP local-first sin red/terceros (ver ADR 0001).
- El log **MUST NOT** introducir esquema ni tabla: es infraestructura, no modelo de
  dominio. Razón: no crear migraciones por observabilidad.

## Géneros y clasificación asistida (revisión 2026-10-15)

- Una Nota **MUST NOT** volver a tener **Etiquetas genéricas libres**: su
  clasificación descriptiva múltiple son los **Géneros** (catálogo fijo constante
  de 12 + personalizados por Nota). Razón: `CONTEXT.md`; el catálogo fijo no debe
  "engordarse" globalmente con cada valor escrito.
- La IA **MAY** **sugerir** `Track`/`Género` (vía `AiClient`, caso de uso `jev`)
  como una acción **explícita y separada** del indexado. La sugerencia **MUST**
  requerir confirmación del usuario (confirmar o editar) y **MUST NOT**
  auto-asignar ni persistir la clasificación sin confirmación. Razón: se revisó el
  non-goal "sin clasificación IA" (2026-10-15); el indexado sigue sin clasificar.
- Las secciones de clasificación del GDD **MUST** derivarse de `Track`/`Género`
  elegidos por el usuario; la sugerencia de IA no reemplaza esa decisión.

## Plataforma (Android-first, iOS congelado)

- Las features **MUST NOT** exigir paridad ni verificación iOS; `init.sh` valida
  **solo Android** (`:composeApp:assembleDebug` + `:core:testDebugUnitTest`) y no
  debe levantar dev servers ni simuladores. Razón: revisión de alcance 2026-10-15
  (`docs/adr/0003-ios-congelado-mvp.md`).
- Todo `expect` nuevo **MUST** aportar un `actual` iOS **mínimo** para no romper el
  build compartido de iOS (aunque iOS no se verifique en el gate). Razón: iOS se
  conserva y debe seguir compilando.

## IA real (proveedor y credenciales)

- La **API key** de IA **MUST NOT** versionarse: se provee por **build flag**
  (env/gradle property, gitignored). Razón: `AGENTS.md` prohíbe secrets.
- Los **tests** y el **gate** (`init.sh`) **MUST** usar la **IA fake**
  (determinista, sin red ni key); el adapter **real** **MUST NOT** ejecutarse en el
  gate. Razón: el gate es offline/ determinista; el costo/red no son verificables
  en CI.
- Proveedor del MVP: **propio, compatible con OpenAI** (`https://inference.devexpert.io/v1`),
  modelos chat/embedding/transcribe/tts/image/jev (ver `docs/adr/0004-ai-real-integration.md`).
- El modo se elige con `playbook.ai.mode` (`PLAYBOOK_AI_MODE`), default `fake`;
  `init.sh` **MUST** forzar `-Pplaybook.ai.mode=fake`. La key se toma de
  `playbook.ai.apiKey` / `PLAYBOOK_AI_API_KEY` / `local.properties`
  (`local.properties` está gitignored). En modo fake, `BuildConfig.AI_API_KEY`
  **MUST** quedar vacío.
- `real` **MUST** ser el default sólo en debug. Un release real **MUST** requerir
  opt-in por propiedad Gradle (`-Pplaybook.ai.allowReleaseKey=true`); env/
  `local.properties` **MUST NOT** habilitarlo. Deuda conocida: `findProperty`
  también resuelve `gradle.properties`/`ORG_GRADLE_PROJECT_*`; el hardening a
  sólo `-P` queda pendiente. Un modo real sin key/base URL/modelos **MUST**
  fallar el build con un mensaje accionable (sin imprimir la key).
- El transporte HTTP de IA **MUST NOT** seguir redirects automáticos: un 3xx
  **MUST** mapearse a `AiClientException` genérica sin reenviar key, headers ni
  body a otro host. Razón: un 307 podría filtrar credenciales/datos.
- El I/O de red de IA **MUST** ejecutarse fuera del hilo Main (`Dispatchers.IO`);
  el transporte Android lo hace por defecto y permite inyectar el dispatcher en
  tests. Razón: `NetworkOnMainThreadException`/ANR en indexado y RAG reales.
- La dimensión del embedding real es **1536** (`playbook.ai.embeddingDimension`);
  los embeddings de otra dimensión se reconcilian a `PENDING`/`index_attempts=0`
  antes del reintento de arranque (`EmbeddingDimensionReconciler`). Razón:
  `ai-fake-real-switch`.
- Todo embedding generado por el runtime real **MUST** medir
  `playbook.ai.embeddingDimension` (1536). Un mismatch **MUST** rechazarse antes
  de persistir, **MUST NOT** pisar un embedding previo y **MUST** seguir el ciclo
  `PENDING`/reintento existente (`NoteIndexingService.expectedDimension` +
  guard en `OpenAiAiClient.embed`); el mensaje/diagnóstico **MUST NOT** incluir
  key, headers ni body. Razón: un vector de otra dimensión corrompe
  Enlaces/RAG, y el límite de persistencia no puede depender sólo del proveedor.
  Un mismatch persistente agota el presupuesto y termina en `FAILED` según
  `offline-pending-retry` (no se agrega sonda de conectividad en esta feature).

## GDD

- El GDD **MUST** ser **híbrido**: ficha estructurada del **Proyecto** + proyección
  de **Notas** agrupadas por `Track`. **MUST NOT** persistir una tabla que duplique
  las Notas. Razón: `docs/domain-model.md`.
