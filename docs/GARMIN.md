# Integración con Garmin

## Vía elegida: API personal de Garmin Connect

Desde octubre de 2026 Garmin ofrece una clave de API personal para que cada usuario acceda a sus
propios datos (Garmin Connect Developer Program, nivel «Personal»). No requiere solicitud ni aprobación.

Pasos para el usuario:

1. Entrar en Garmin Connect **desde la web** (no desde la app del móvil).
2. Abrir Ajustes › *Personal API Key* (connect.garmin.com/app/settings/personalApiToken).
3. Pulsar **Create Key**, aceptar los términos y elegir la caducidad.
4. Copiar la clave: solo se muestra una vez. Guardarla como una contraseña.
5. En Tu Pedalada (beta 3): Importar › Garmin Connect › pegar la clave. Se guarda cifrada con Android Keystore.

Implementación (`garmin/`): token con `POST https://personal.connectapi.garmin.com/personalapitoken-service/personal-api/personal-api-key/token`
(cabecera `Authorization: ApiKey <clave>`, respuesta = token JWT válido 24 h); actividades con
`GET /activitylist-service/activities/search/activities?start&limit&startDate&endDate` (cabecera
`Authorization: Bearer <token>`). Rutas tomadas de la librería oficial de Garmin `garmin-connect-personal-api-python`.
Unidades: metros, segundos, m/s. La clave caduca según lo elegido al crearla (30 días a 1 año).

Qué da: actividades (resumen JSON y archivo .FIT completo con GPS, FC, cadencia, potencia y vueltas)
y datos de salud. Limitaciones: solo «pull» (la app pide los datos al abrirse; nada llega solo),
no permite enviar rutas ni entrenamientos al reloj, y Garmin puede omitir algunos campos internos
(VO2 máx, tiempo de recuperación).

## Alternativas que se mantienen

- Archivo .FIT copiado del Edge 830 / Enduro 1 o exportado desde Garmin Connect.
- GPX / TCX.
- Health Connect (si Garmin Connect Mobile está sincronizando con él).

No se usan accesos no oficiales ni se guarda la contraseña de Garmin.
