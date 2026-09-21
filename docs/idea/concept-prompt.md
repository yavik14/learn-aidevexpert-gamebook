# Prompt conceptual de aplicación

## Rol
Actúa como co-diseñador de producto y arquitecto de software mobile. Vamos a iterar
sobre la idea conceptual de una app descrita abajo. No implementes todavía: ayudame a
refinar alcance, diseño y arquitectura.

## Objetivo del proyecto
Proyecto de APRENDIZAJE personal. Prioriza aprender sobre lanzar a producción. El foco
de aprendizaje es doble: (1) integración de IA en una app real, y (2) diseño y
desarrollo de videojuegos 2D (como afición).

## Nombre tentativo
"Playbook" — Notebook de diseño de juegos con IA.

## Concepto
Una app de notas pensada para game designers/desarrolladores. El usuario captura ideas
en texto, voz o foto de bocetos en papel (mecánicas, niveles, narrativa, arte). La IA
las clasifica automáticamente por categoría, las enlaza entre sí y va construyendo un
Game Design Document (GDD) vivo y navegable. El usuario puede consultar en lenguaje
natural ("¿qué mecánicas todavía no tienen nivel asignado?") y obtener respuestas
basadas en sus propias notas.

## Visión de escalado (fase posterior, NO en el MVP)
Convertirse en un Level Design Lab: un editor de niveles 2D (tilemaps con Canvas) donde
la IA genera niveles desde un prompt de texto, evalúa dificultad, verifica que sean
completables, arma la curva de dificultad y exporta a formatos usables en Godot/Unity/
LÖVE (ej. Tiled .tmx/.json). Opcionalmente, un mini-playtest con física básica dentro
de la app. El MVP debe diseñarse de forma que esta fase se agregue sin reescribir el core.

## Usuario
Principalmente yo (game dev aficionado). Secundariamente, otras personas que diseñan
juegos y toman notas dispersas.

## Stack técnico
- Core de lógica compartida en Kotlin Multiplatform (KMP).
- UI en Compose Multiplatform.
- Usar código/integraciones nativas solo cuando sea necesario (cámara, speech-to-text,
  capacidades on-device de IA, etc.).
- Persistencia local de notas (a definir: SQLDelight / Room KMP).
- Búsqueda semántica con embeddings (RAG) sobre las notas del usuario.

## Alcance del MVP (fase A)
1. Crear/editar/borrar notas, con categoría (mecánica, nivel, narrativa, arte, otro).
2. Captura por texto y voz; (foto de bocetos si la integración nativa es viable).
3. Clasificación automática y sugerencia de etiquetas por IA.
4. Enlazado/relacionado de notas por similitud semántica.
5. Vista de GDD que agrupa y resume el contenido.
6. Consulta en lenguaje natural sobre las notas (RAG).

## Decisiones ABIERTAS (a resolver iterando)
- Runtime de IA: (A) todo en la nube vía API, (B) todo on-device, (C) híbrido detrás
  de una interfaz `AiClient`. IMPORTANTE: diseñar `AiClient` como abstracción para poder
  cambiar de estrategia sin tocar el core.
- Plataformas objetivo (Android, iOS, Desktop, Web) — confirmar cuáles y en qué orden.
- Modelo/DB local y estrategia de embeddings.
- Manejo de API keys y costos si se usa nube.
- Qué se hace offline vs online.

## Criterios de éxito (aprendizaje)
- Integración de IA funcional en un flujo real, no un chatbot decorativo.
- Código compartido real en KMP (no lógica duplicada por plataforma).
- Al menos una integración nativa resuelta correctamente.
- Base de código que permita escalar a la fase B sin refactor mayor.

## Formato de tu respuesta
Primero resumí en 3-5 líneas qué entendiste. Después proponé 2-3 enfoques con
trade-offs y tu recomendación. Hacé UNA pregunta a la vez para cerrar las decisiones
abiertas.