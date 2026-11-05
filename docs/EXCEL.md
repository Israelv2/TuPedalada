# Análisis del Excel histórico (TU PEDALADA.xlsx)

Analizado el 05/11/2026. Archivo de 18,4 MB con 8 hojas: INDICE, Dashboard, CALCULOS, Tabla,
BIKE, WALKING, RUNNING, Hoja1.

**Decisión del usuario:** importar solo la hoja **«Tabla»**.

## Hoja «Tabla»

- 2 859 filas: BIKE 2 720 · WALKING 107 · RUNNING 32. Periodo 03/07/2015 – 14/09/2026.
- 27 columnas: tipo, año, mes, fecha, hora de comienzo y llegada, ruta, distancia, calorías, FC media
  y máxima, velocidad media y máxima, equipo, temperaturas, tiempos en movimiento y transcurrido,
  alturas, ascenso, descenso, zancada, pasos, mejor vuelta, ritmo medio y óptimo.
- Equipos: Scott Spark 730, Aluminio 29er, Fixie china, Canyon nerve XC, Ascis Cumulus 14,
  Skechers air cooled, Adidas AX 2.0 GTX. 88 filas sin equipo y 11 con «0» (se importan sin equipo).

## Reglas de lectura

| En el Excel | En la app |
|---|---|
| `--`, `--:--:--`, vacío | Sin dato (null). Nunca se rellena |
| `16,8` (texto) | 16.8 |
| `12.098` en pasos | 12 098 |
| Hora de comienzo `00:00` | Hora desconocida (38 filas) |
| Tiempos como número o texto `01:53:07` | Segundos |
| Ritmo `12:16` | 736 s/km |
| Velocidad media vacía en bici | Se calcula = distancia / tiempo en movimiento y se marca C |

## Duplicados

Dos filas son la misma actividad si coinciden modalidad, fecha, hora (si existe), distancia,
calorías y tiempo transcurrido. Con esa regla hay 27 repeticiones exactas dentro de «Tabla».

Cada actividad guarda la fila original completa y su referencia («TU PEDALADA.xlsx › Tabla › fila N»),
para poder recalcular en el futuro sin volver al Excel.

## Observación

En la hoja WALKING aparecen equipos «Ascis Cumulus 15» a «23» en filas consecutivas: parece un
arrastre automático de Excel a partir de «Ascis Cumulus 14». No afecta a «Tabla».
