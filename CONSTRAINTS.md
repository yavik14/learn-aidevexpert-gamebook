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

- El pipeline de indexado **MUST NOT** clasificar ni etiquetar la Nota (la IA no
  asigna `categoría`/`tipo`/`Etiquetas`) ni bloquear la captura: un fallo de IA
  deja la Nota `PENDING` y nunca lanza hacia el llamador. Razón: la clasificación
  IA está descartada (`CONTEXT.md`) y la captura es local-first/tolerante a
  fallos.
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
