# Speed Launcher

Stock-like Material You launcher per Android 12+. Single-codebase Kotlin nativo.

## Cosa fa la v1

- **Home page** con griglia 4×5 di icone app (auto-popolata con le prime 4 app installate al primo avvio)
- **Search bar Pixel-style** in basso (apre il drawer con tastiera già aperta)
- **App drawer** bottom-sheet a tutto schermo, con search filtro che ignora accenti/maiuscole
- **Widget slot singolo** in alto: long-press per pickerare un widget, long-press di nuovo per rimuoverlo
- **Notification dots** sulle icone home (richiede attivazione manuale del Notification Listener nelle Impostazioni)
- **Material You** colori dinamici dal wallpaper (Android 12+)
- **Edge-to-edge** trasparente, mostra il wallpaper di sistema
- **i18n** Italiano + Inglese

## Setup

1. Apri la cartella in **Android Studio Hedgehog (2023.1.1)** o successivo
2. Lascia Gradle scaricare le dipendenze (~5 min al primo avvio)
3. Connetti un device Android 12+ con debug USB attivo
4. Run ▶ Build & install

### JDK richiesto
Java 17 (di solito già incluso in Android Studio recenti).

## Pubblicazione su Play Store

### 1. Genera un keystore di release

Da terminale:

```bash
keytool -genkey -v -keystore speed-launcher.jks -keyalg RSA -keysize 2048 -validity 25000 -alias speed
```

Salvalo **fuori dal progetto** e backuppalo. Se lo perdi non puoi più aggiornare l'app.

### 2. Configura signing in Android Studio

`File → Project Structure → Modules → app → Signing Configs` oppure aggiungi a `app/build.gradle`:

```groovy
signingConfigs {
    release {
        storeFile file("/path/to/speed-launcher.jks")
        storePassword "..."
        keyAlias "speed"
        keyPassword "..."
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release
        // ...
    }
}
```

Meglio: usa variabili d'ambiente o `~/.gradle/gradle.properties` per non committare le password.

### 3. Genera Android App Bundle

`Build → Generate Signed App Bundle / APK → Android App Bundle → release`

L'output è in `app/release/app-release.aab`.

### 4. Play Console

- Crea l'app, compila la scheda (titolo, descrizione, screenshot, icona Play Store 512×512)
- **Privacy policy obbligatoria** — il NotificationListenerService è "sensitive permission". Devi avere una pagina online (su cheipstudio.org va benissimo) che spieghi:
  - Che leggi la lista delle app installate (per mostrarle nel drawer)
  - Che usi l'accesso alle notifiche SOLO per contare i pallini per app, e che il contenuto delle notifiche **non viene letto né trasmesso**
  - Che non ci sono server: tutto è locale
- Per la Data Safety form, dichiara: nessun dato raccolto, nessun dato condiviso

### 5. Note di review

Google chiederà perché hai bisogno del notification listener. Risposta tipo:
> "Speed Launcher uses notification access exclusively to display unread notification badges (dots) on app icons, mirroring the standard AOSP launcher behavior. Notification content is never read, parsed, displayed, stored, or transmitted. The service only counts active notifications per package."

## Architettura

```
SpeedApp (Application)
├── AppRepository       → carica e osserva le app via LauncherApps
├── HomeLayoutStore     → JSON in SharedPreferences (le tue app pinnate)
└── NotificationCounter → mappa package → count

MainActivity
├── HomeView
│   ├── WidgetSlotView    → AppWidgetHost
│   ├── IconGridView      → griglia 4x5, custom dot drawing
│   └── search bar        → apre il drawer
└── AppDrawerSheet        → BottomSheetDialogFragment con RecyclerView 4col

WidgetHostController     → gestisce pick + bind + configure dei widget
SpeedNotificationListener → service per i dots
```

## Cosa NON c'è (roadmap)

- Drag & drop per pinnare/spostare icone (al momento il dock di default sono le prime 4 app)
- Multi-page home con page indicator
- Cartelle
- Long-press menu su icone (info app, disinstalla, shortcut dinamici)
- Theming custom oltre a Material You
- Multi-user / work profile
- Backup / restore del layout

Tutto il resto è progettato per essere esteso facilmente.

## Limiti noti

- Il widget host accetta UN solo widget alla volta nello slot in alto (semplificazione v1)
- Senza permesso Notification Listener attivo, i dots semplicemente non appaiono (silently no-op)
- Le icone usano `getBadgedIcon(0)` di LauncherApps, non c'è icon-pack support

---

Questo progetto è un launcher originale scritto in Kotlin da zero — non è un fork di Launcher3 / AOSP. Pubblicabile su Play Store senza grane di derivative work.
