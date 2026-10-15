# ADR 0003: Congelar iOS en el MVP (Android-first)

- **Status:** propuesto (dirección aprobada por el autor el 2026-10-15; pendiente
  de promoción a `accepted` si corresponde).
- **Fecha:** 2026-10-15
- **Contexto de origen:** revisión de alcance del MVP (discovery, fase build-brief).
- **Decisores:** autor del producto (game dev aficionado).
- **Relacionado:** `docs/build-brief.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md`, `CONTEXT.md`, `ARCHITECTURE.md`.

## Contexto

Playbook es una app KMP (Android + iOS en paralelo) de notas de diseño de juegos,
local-first y single-user, con UI en Compose Multiplatform y dominio compartido en
`:core`. Hasta ahora cada feature se construyó para **ambas** plataformas (STT y
cámara nativas, audio, release iOS) y el gate `./init.sh` compila/linkea el
framework iOS y corre los tests del core también en iOS.

El autor quiere **ampliar el MVP** (pantalla principal rediseñada, flujo de
Brainstorming del Proyecto, GDD como documento, bocetos con miniatura, dictado de
30s, Géneros) y **concentrarse en Android**. Construir cada pieza nueva para dos
plataformas **duplica el esfuerzo nativo** y retrasa el primer flujo end-to-end
(riesgo ya registrado en `docs/risks-and-open-questions.md`).

Restricciones relevantes:

- El objetivo es **aprender** (no un producto comercial); el alcance puede
  recortarse si amenaza el aprendizaje.
- El código es **compartido en `:core`**; `expect/actual` sólo aparece en
  `:composeApp` (voz, cámara, audio, storage).
- El gate debe seguir siendo **no bloqueante** y sin simuladores.
- Entorno: macOS + Xcode disponible, pero mantener iOS exige trabajo y verificación
  continuos.

## Criterios de decisión

1. **Velocidad de aprendizaje / time-to-end-to-end** del flujo completo.
2. **Costo de mantenimiento** de la paridad Android/iOS.
3. **Reversibilidad** (poder retomar iOS sin refactor mayor).
4. **Salud del repo** (el gate debe seguir siendo confiable y ligero).
5. **Alineación con el objetivo** (producto de aprendizaje, single-user).

## Alternativas consideradas

### A. Mantener iOS (Android + iOS en paralelo)

Seguir construyendo y verificando cada feature en ambas plataformas.

- **A favor:** paridad real; se ejercita KMP "de verdad"; no hay deuda de
  descongelado.
- **En contra:** **duplica** el trabajo nativo y la verificación; retrasa el flujo
  end-to-end; el foco se dispersa en features grandes nuevas (Brainstorming, GDD).

### B. Android-only estricto (diferir iOS sin conservar build)

Quitar iOS del gate y no escribir `actual` iOS; el build iOS puede romperse sin
bloquear el trabajo Android.

- **A favor:** menos fricción aun que "congelar"; no obliga a mantener `actual`s.
- **En contra:** deja el proyecto iOS **roto** (deuda mayor); rompe la promesa de
  KMP compartido y complica retomar.

### C. Congelar iOS (Android-first, conservar y compilar)

iOS sale del alcance y del gate, pero el código y el target se conservan; los
nuevos `expect` aportan un `actual` iOS mínimo para no romper el build compartido.

- **A favor:** máxima velocidad en Android; el repo sigue **compilando** iOS; el
  costo de retomar es bajo (el seam `expect/actual` queda intacto).
- **En contra:** la paridad de comportamiento se congela (iOS no evoluciona);
  requiere disciplina para escribir `actual`s mínimos.

### D. Eliminar iOS del repo

Borrar `iosApp/`, `iosMain` y los targets iOS.

- **A favor:** proyecto netamente Android, sin ruido.
- **En contra:** **destructivo** y difícil de revertir; se pierde el valor de
  aprendizaje de KMP y todo el trabajo previo.

## Decisión

**Se elige C: congelar iOS (Android-first).**

1. iOS sale del **alcance** del MVP y de la **verificación**; `init.sh` pasa a ser
   **Android-only** (`:composeApp:assembleDebug` + `:core:testDebugUnitTest`).
2. Se **conserva** el código (`iosApp/`, `iosMain`) y el **target iOS sigue
   compilando**; no se elimina nada.
3. Los nuevos `expect` deben seguir aportando un `actual` iOS **mínimo** para no
   romper el build compartido (aunque no se verifiquen).
4. El runtime de IA (`AiClient`) y `:core` no cambian por esta decisión.

## Consecuencias

**Positivas**

- Foco y velocidad en Android; menos trabajo nativo y de verificación.
- El flujo end-to-end del MVP llega antes.
- El repo sigue **compilando** iOS y el seam `expect/actual` queda intacto: retomar
  es barato.

**Negativas / costos**

- La paridad de comportamiento iOS se **congela**; evidencia iOS no se produce.
- Disciplina: cada `expect` nuevo necesita un `actual` iOS mínimo (aunque trivial).
- El ADR de release iOS (`ios-release-pipeline-scaffolding`) queda verificado pero
  **fuera de evolución** hasta descongelar.

**Neutras**

- No cambia el esquema ni `:core`; es una decisión de **alcance/plataforma**.
- `docs/technical-discovery.md`, `docs/build-brief.md`,
  `docs/risks-and-open-questions.md` y `DESIGN.md` registran el congelamiento.

## Disparadores de re-evaluación

Descongelar iOS (y revisar este ADR) si ocurre **cualquiera** de:

- El flujo Android del MVP está completo y estable, y se quiere paridad.
- Se necesita publicar/validar en iOS (TestFlight, dispositivo real).
- El proyecto pasa a considerarse producto (no sólo aprendizaje).

## Preguntas abiertas

- ¿Cuándo (condición concreta) se descongela iOS? (criterio de salida del
  congelamiento).
- ¿Se fija una cadencia de "chequeo de build iOS" o se deja totalmente fuera hasta
  descongelar?
