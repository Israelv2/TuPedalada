# Estado de la Entrega B

## Beta 3 (0.4.0-beta3) · 05/11/2026

- **Sincronización con Garmin Connect** mediante la API personal (clave del usuario, cifrada con
  Android Keystore). Se ejecuta al desbloquear la app, con internet, como mucho cada 10 minutos, y
  con el botón «Sincronizar ahora». Pide desde la última sincronización (con 3 días de margen); la
  primera vez, desde la última actividad guardada.
- Las actividades nuevas quedan **pendientes de confirmar** (pantalla 06): modalidad y equipo, con el
  último equipo usado en esa modalidad propuesto por defecto. Después se abre el registro de
  hidratación (pantalla 17).
- Sin duplicados: se descartan las que ya existen por su identificador de Garmin o porque coinciden
  en modalidad, día, hora:minuto y distancia con lo importado del Excel o del CSV.
- Base de datos v2 con migración que conserva los datos (nuevas columnas `garminActivityId`, `pendingConfirm`).
- Firma fija de las betas mediante secretos de GitHub: a partir de esta versión las actualizaciones
  se instalan encima sin perder datos.

## Beta 2 (0.3.0-beta2) · 05/11/2026

Cambios pedidos tras probar la beta 1 en el teléfono:

1. **Textos negros sobre fondo oscuro**: corregido. El color de texto por defecto pasa a crema en
   toda la app (los textos sin color explícito salían en negro).
2. **Histórico directo en cada modalidad**: en Running, Walking y Biking aparecen casillas por año;
   al tocar un año, casillas por mes (los 12, los vacíos apagados); al tocar un mes, sus actividades.
   El historial con filtros sigue disponible («Buscar con filtros»).
3. **Importación de CSV**: el «Activities.csv» de Garmin Connect (español o inglés, coma o punto y coma).
   La hora se toma de la columna Fecha. Los duplicados se detectan también contra lo importado desde
   el Excel (misma modalidad, día, hora:minuto y distancia). Las modalidades distintas de bici,
   carrera y caminata (natación, fuerza…) se listan como no admitidas y no se importan.

## Beta 1 (0.2.0-beta1) · 05/11/2026

## Implementado

| Pantalla | Función real |
|---|---|
| 02 Acceso | Creación de PIN de 6 cifras con confirmación, código de recuperación de 12 caracteres (se muestra una vez), desbloqueo con PIN, huella (BiometricPrompt), 5 intentos y bloqueo progresivo (30 s → 15 min) |
| A02 Recuperar | Con código de recuperación o huella → nuevo PIN |
| 01 Splash | Vídeo «tu pedalada» tras el desbloqueo, con «Saltar» |
| 03 Dashboard | Datos reales de la base de datos: hoy, última actividad, semana por días, mes vs. mes anterior, peso, calendario |
| 04 Menú | Cajón lateral con todas las secciones |
| 05 Importación | Selección del Excel, estado de las integraciones, historial de sincronización |
| A03 Asistente Excel | Hoja «Tabla»: copia íntegra del original (SHA-256), vista previa, duplicados en el archivo y en la app, errores y avisos, importación en una transacción |
| 07/08/09 Running/Walking/Biking | Totales semana/mes/año, velocidad o ritmo calculados, FC, últimas actividades, km por equipo |
| 10 Detalle | Todos los campos con distintivo M (medido) / C (calculado) / E (estimado); «—» si el dato no existe |
| 13 Historial | Filtros por año, mes, modalidad, equipo y ruta |
| 15 Perfil | Datos personales, salud, parámetros deportivos, IMC calculado |
| 17 Hidratación | Agua, otra bebida, alimento, fatiga, RPE y recuperación |
| A04 Peso diario | Un registro por día (si se repite, se actualiza) |
| 19 Configuración | Huella sí/no, bloqueo automático, cambio de PIN, versión |
| Seguridad | Bloqueo automático al volver a la app tras el tiempo configurado; sin copia automática a la nube |

Pruebas unitarias: lectura de celdas del Excel (guiones, comas, miles, fechas, horas, duraciones),
importador de «Tabla» (calidad, origen, duplicados, hora desconocida, errores) y fórmulas.

## Resultado esperado con TU PEDALADA.xlsx (hoja «Tabla»)

Simulado con las mismas reglas sobre el archivo real: **2 832 actividades** a importar,
**27 duplicadas exactas** omitidas, **0 errores**, **38** sin hora de inicio (se guardan con la fecha).

## Pendiente (beta 2)

- Importación FIT (Garmin FIT SDK), GPX y TCX, con muestras y puntos GPS.
- Garmin Connect mediante la API personal (clave del usuario) y Health Connect.
- 11 Mapa (MapLibre + OSM), 12 Comparativos, 16 Evolución, 18 Análisis de rutas.
- 14 Equipamiento (edición, fotos, vida útil), 20 Actualizaciones, 21 Copias y exportación.
- Configuración de colores, iconos, tipografía Barlow, unidades y notificaciones.
- Recordatorio de peso al final del día (WorkManager).

Las entradas del menú de estas secciones abren una pantalla que indica que están en desarrollo;
no hay botones que simulen funciones inexistentes.
