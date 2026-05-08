=================================================
SpeedLauncher v52 — IMPORTANTE
=================================================

Il build v51 ha fallito per via di un file fantasma nel repo:
  ".trashed-1780816905-TutorialOverlay.kt"

Questo è un residuo del file system Android (Termux usa un cestino interno).
DEVI ELIMINARLO PRIMA DI APPLICARE v52.

PASSI IN TERMUX:

1. Vai nella cartella del progetto:
   cd ~/storage/downloads/SpeedLauncher/SpeedLauncher/SpeedLauncher

2. Trova ed elimina TUTTI i file ".trashed-*":
   find . -name ".trashed-*" -delete

3. Verifica che siano spariti:
   find . -name ".trashed-*"
   (deve essere vuoto)

4. Applica v52:
   unzip -o ~/storage/downloads/SpeedLauncher_v52_files.zip

5. Commit:
   git add -A
   git commit -m "v52: rimosso file trashed, widget hardened"
   git push

=================================================
WIDGET SPEED STATS — verificato
=================================================

Il widget v50 è già funzionante (testato).
v52 aggiunge solo try/catch hardening su tutti i system call
(memory, storage, battery) per non crashare su device esotici.

Per usarlo:
- Long press sul widget vuoto della home
- Seleziona "Speed Stats" (è il primo della lista nei widget custom)
- Il widget appare con RAM, memoria e batteria

Theme/auto-refresh si configurano dalle impostazioni nella sezione
"Widget Speed Stats".
