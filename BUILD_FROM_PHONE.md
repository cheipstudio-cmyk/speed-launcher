# Build APK dal telefono — Guida rapida

Tutto questo si fa dall'app **GitHub** sul tuo Android (oppure browser mobile su github.com).

## Prima volta — Setup (10-15 min)

### 1. Crea il repo su GitHub

- Apri **github.com** dal browser del telefono → login col tuo account
- Pulsante **"+"** in alto a destra → **New repository**
- Nome: `speed-launcher` (o quello che preferisci)
- Visibilità: **Private** (consigliato fino a quando non sei pronto a pubblicare il codice; le Actions funzionano lo stesso, hai 2000 minuti gratis al mese sui repo private)
- NON inizializzare con README (ce l'abbiamo già)
- Click **Create repository**

### 2. Carica il progetto sul repo

Hai due opzioni:

**Opzione A — Upload del .zip estratto da PC (più semplice)**

Se hai accesso a un PC anche solo 5 minuti:
- Estrai lo zip
- Sulla pagina del repo vuoto: **"uploading an existing file"**
- Trascini tutta la cartella `SpeedLauncher` dentro
- Commit message: `Initial commit`
- Commit changes

**Opzione B — Tutto da telefono con app Acode/GitHub Mobile**

- Installa **Acode** dal Play Store (editor codice gratuito)
- In Acode: clona il repo vuoto via HTTPS con il tuo Personal Access Token
- Importa lo zip estratto in Acode (apre la cartella)
- Da Acode: stage tutti i file → commit → push

(L'opzione A è 10x più veloce, davvero. Se hai un PC anche solo per il primo upload, usalo.)

### 3. La Action parte da sola

Appena pushi:
- Vai sulla tab **"Actions"** nel tuo repo (su github.com)
- Vedrai il workflow **"Build APK"** in esecuzione (icona gialla = running)
- Tempo: **5-8 minuti** la prima volta (deve scaricare Gradle/SDK), 2-3 minuti dalla seconda

### 4. Scarica l'APK sul telefono

Quando il job è verde ✓:
- Clicca sul run → scrolla in fondo → sezione **"Artifacts"**
- Vedrai `SpeedLauncher-debug-1` (e magari `release-unsigned`)
- Click → scarica un file `.zip` (è un limite di GitHub, gli artifact sono sempre zippati)
- Estrai il .zip → trovi `SpeedLauncher-debug-1.apk`
- **Tappa l'APK** → Android ti chiede "Vuoi installare app da fonti sconosciute?" → Concedi a Files/Chrome → Installa

### 5. Imposta come launcher

- Tasto Home del telefono → ti chiede quale launcher usare → **Speed Launcher** → "Sempre"

## Cicli successivi (modifica → push → APK)

Una volta che il setup c'è, ogni modifica funziona così:

1. Mi dici cosa modificare / cosa non funziona
2. Ti mando il diff o il file aggiornato
3. Lo carichi su GitHub (web editor: tappa il file → matita → incolla → Commit)
4. La action parte da sola
5. 3 minuti dopo scarichi nuovo APK e lo installi sopra il vecchio

## Differenze tra debug APK e release APK

Il workflow ti genera DUE APK ad ogni push:

- **debug APK**: si installa subito, ma è più lento e pesante (no shrinking, no minify, debugger attivo)
- **release APK unsigned**: ottimizzato come quello che andrà sul Play Store, ma **non firmato** quindi Android lo rifiuta in installazione

Per testare → usa **debug APK**.
Per Play Store → ti dovrai firmare il release APK localmente (o aggiungere una signing key cifrata al repo, ti spiego dopo se serve).

## Permessi che dovrai concedere manualmente

Dopo l'installazione, prima di tutto funziona, vai in Impostazioni:

1. **Impostazioni → App → Speed Launcher → Imposta come predefinito** (per il tasto Home)
2. **Impostazioni → Notifiche → Accesso alle notifiche** → attiva **Speed Launcher** (per i pallini di notifica)

## Troubleshooting

**"Build failed" rossa**

- Vai sulla run fallita → leggi i log (clicca il job rosso → expand i passaggi)
- Copiami l'errore in chat e te lo sistemo

**"Action queued for too long"**

- Capita ogni tanto, aspetta o re-run dalla UI

**Quota Actions superata (improbabile con un solo progetto)**

- Repo private hanno 2000 min/mese free. Una build = ~5 min → 400 build/mese
- Per repo public sono illimitate

**APK installato ma il launcher non parte**

- Probabilmente errore al runtime. Connetti USB a un PC e fai `adb logcat` per vedere il crash
- Oppure usa un'app come **LogFox** (Play Store) che mostra logcat direttamente sul telefono senza root

---

Una volta che hai il primo APK installato e funzionante, il loop è:

> "questa cosa non mi piace" → mi mandi screenshot/descrizione → io ti mando file aggiornato → push → 3 min → nuovo APK
