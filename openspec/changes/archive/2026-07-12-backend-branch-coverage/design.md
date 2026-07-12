## Context

Informe JaCoCo del CI (unit + IT): ramas 65,2 % (608/933), líneas 89,3 %. El gate `jacoco-check` del `pom.xml` solo comprueba `LINE COVEREDRATIO minimum 0.80`. Las ramas sin cubrir se concentran en validaciones/guards de servicios y en `equals`/builders/branching de modelos de dominio.

## Goals / Non-Goals

**Goals:** subir ramas globales a ≥80 % (o lo máximo práctico) atacando las clases con más ramas perdidas; añadir un gate de ramas para no regresar.
**Non-Goals:** refactor de producto; perseguir el 100 % en ramas defensivas inalcanzables.

## Decisions

### D1 — Priorizar por ramas perdidas (mayor ROI)
Se atacan primero las clases con más ramas sin cubrir (ver lista en el proposal). Cada rama nueva cubierta acerca al objetivo global; las clases 0 %-ramas (`AdminReservaService` 20/20) dan el mayor salto.

### D2 — Tests de rama, no de línea
Los tests apuntan a las CONDICIONES: cada `if/else`, `?:`, `switch`, validación (`throw` vs éxito), `Optional.orElse`, null-checks, y ramas de `equals`/`hashCode` de los modelos. Se usan mocks para servicios (sin BD) y construcción directa para dominio.

### D3 — Ramas inalcanzables / defensivas
Si una rama no es alcanzable por diseño (p. ej. `default` de un switch exhaustivo, o un guard imposible), se documenta y, si procede, se excluye de JaCoCo con justificación — nunca se fuerza un test artificial.

### D4 — Gate de ramas prudente
Tras subir la cobertura, se añade a `jacoco-check` un límite `BRANCH COVEREDRATIO minimum <valor>` fijado al nivel alcanzado **menos un pequeño margen** (p. ej. si se alcanza 80 %, gate a 0.78), para evitar falsos rojos por fluctuación pero blindar la mejora. El límite de líneas se mantiene en 0.80.

## Risks / Trade-offs

- **[La cobertura de ramas del CI (unit+IT) difiere de la medible en local (solo unit por Docker 29)]** → Mitigación: los nuevos tests son UNITARIOS (miden en local con `mvn test jacoco:report`); el % global final se confirma en el informe JaCoCo del CI. El gate se fija con margen.
- **[Tests frágiles que solo suben el número]** → Mitigación (D2): tests que verifican comportamiento real de cada rama, no asserts vacíos.
- **[Gate de ramas demasiado alto → CI rojo intermitente]** → Mitigación (D4): margen prudente bajo el valor alcanzado.

## Migration Plan

1. Añadir tests unitarios por clase (lista priorizada).
2. Medir cobertura de ramas (local unit + confirmar en CI).
3. Añadir el gate de ramas a `jacoco-check` al nivel alcanzado − margen.
4. Rollback: revertir tests/gate (no afecta a producto).
