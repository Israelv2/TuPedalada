# Tu Pedalada — Personal Sports Tracker (Android)

App personal para registrar, importar y analizar actividades de **Running, Walking y Biking**,
inspirada en Garmin Connect. Kotlin + Jetpack Compose + Room. Funciona sin conexión.

| Entrega | Estado |
|---|---|
| A · Prototipo visual (25 pantallas) | **Aprobada** 05/11/2026 |
| B · Beta funcional | En curso — beta 1 (ver [docs/ESTADO_BETA.md](docs/ESTADO_BETA.md)) |
| C · APK definitiva firmada | Pendiente de aprobación de la beta |

## Compilar

GitHub Actions compila en cada `push` a `main`: pruebas unitarias + APK de pruebas.
La APK se descarga desde la pestaña **Actions › última ejecución › Artifacts › tu-pedalada-beta-apk**.

En local (Android Studio Ladybug o superior, JDK 17):

```
./gradlew testDebugUnitTest assembleDebug
```

## Estructura

```
app/src/main/java/com/tupedalada/app/
  data/db/          Room: 17 entidades, DAOs, base de datos
  data/             Repositorios (importación)
  importer/excel/   Lector .xlsx en streaming + importador de la hoja «Tabla» + fórmulas
  security/         PIN (PBKDF2 + HMAC en Android Keystore), huella, bloqueo
  ui/               Tema, componentes y pantallas Compose
docs/               Estado de la beta, fórmulas, Excel, Garmin
```

## Privacidad

Los datos solo viven en el teléfono. La copia en la nube de Android está desactivada para la
app; las copias se harán desde la propia app, cifradas. No hay credenciales ni datos personales
en este repositorio.
