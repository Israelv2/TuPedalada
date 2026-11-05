# Fórmulas

Implementadas en `importer/excel/Formulas.kt`, con pruebas en `TablaImporterTest`.
Si falta un dato de entrada, el resultado es «sin dato»: no se estima en silencio.

| Indicador | Fórmula | Tipo |
|---|---|---|
| Velocidad media | distancia (km) / tiempo en movimiento (h) | Calculado |
| Ritmo | tiempo en movimiento (s) / distancia (km) | Calculado |
| IMC | peso (kg) / altura (m)² | Calculado |
| FC máxima (si no hay dato en el perfil) | 208 − 0,7 × edad (Tanaka) | Estimado |
| Zonas de FC | 50–60–70–80–90–100 % de la FC máxima | Calculado |

Las calorías del Excel y de los dispositivos Garmin se guardan como **medidas** (las calcula el
dispositivo). Cuando la app tenga que estimarlas se marcarán **E**.
