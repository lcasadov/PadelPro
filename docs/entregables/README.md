# Documentación oficial — PadelPro

Documentos oficiales del proyecto en formato Word (`.docx`), generados el 2026-07-14.

| Documento | Descripción |
|---|---|
| `01-Documento-Funcional.docx` | Alcance funcional, actores, casos de uso (con diagrama), diagramas de secuencia, historias de usuario (MoSCoW) y reglas de negocio. |
| `02-Documento-Arquitectura.docx` | Stack tecnológico, arquitectura hexagonal, diagrama de componentes, referencia a Swagger/OpenAPI (`../openapi.yaml`), modelo de datos (ER) y seguridad/despliegue. |
| `03-Documento-Pruebas.docx` | Estrategia de pruebas, cobertura real (backend JaCoCo, frontend Vitest), pruebas unitarias/integración/E2E y resultados. |
| `04-Manual-de-Usuario.docx` | Guía de uso para jugador y administrador, FAQ y glosario. |

> Al abrir cada Word, actualiza el índice: clic derecho sobre él → «Actualizar campos» (F9).

## Diagramas

Las figuras se generan con [PlantUML](https://plantuml.com). Las fuentes están en `diagramas-fuente/` (`*.puml`). Para re-renderizar a PNG:

```
java -jar plantuml.jar -tpng diagramas-fuente/*.puml
```

Los `.docx` se generan con `python-docx` a partir del contenido real del repositorio (specs OpenSpec, `backlog.md`, `data-model.md`, `openapi.yaml`, `TESTING-STRATEGY.md`) y de las métricas de cobertura/tests medidas en el momento de la edición.
