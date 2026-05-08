package org.cheipstudio.speedlauncher.tools

import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeItem
import org.cheipstudio.speedlauncher.data.HomeLayoutStore

/**
 * v85: auto-popolamento layout home quando il drawer è disabilitato.
 *
 * Strategia:
 * 1. Calcola quali app sono già presenti nel layout (in slot diretti o dentro cartelle).
 * 2. Le rimanenti app, ordinate alfabeticamente, vengono aggiunte:
 *    - Prima negli slot vuoti delle pagine esistenti
 *    - Poi in nuove pagine create dinamicamente
 * 3. Tutte le app aggiunte sono marcate con autoAdded=true.
 *    Quando il drawer viene riabilitato, vengono rimosse (mantenendo solo quelle messe manualmente).
 */
object HomeAutoPopulator {

    /**
     * Aggiunge tutte le app non presenti nel layout. Salva il risultato.
     * Chiamare quando: l'utente disabilita il drawer, o nuova app installata e drawer è OFF.
     */
    fun populate(store: HomeLayoutStore, gridCols: Int, gridRows: Int) {
        val cellsPerPage = gridCols * gridRows
        val allApps = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val hidden = SpeedApp.instance.settingsRepository.hiddenApps.value ?: emptySet<String>()
        val available = allApps
            .filter { !hidden.contains(it.key) }
            .sortedBy { it.label.lowercase() }

        val current = store.load().toMutableList()
        val presentKeys = collectPresentKeys(current)

        val toAdd = available.filter { !presentKeys.contains(it.key) }
        if (toAdd.isEmpty()) return

        // Trovo slot vuoti pagina per pagina
        val maxPage = current.maxOfOrNull { it.page } ?: -1
        val occupiedByPage = current.groupBy { it.page }
            .mapValues { (_, items) -> items.map { it.cellX to it.cellY }.toMutableSet() }
            .toMutableMap()

        val toAddQueue = ArrayDeque(toAdd)
        var page = 0
        while (toAddQueue.isNotEmpty()) {
            val occupied = occupiedByPage.getOrPut(page) { mutableSetOf() }
            for (y in 0 until gridRows) {
                for (x in 0 until gridCols) {
                    if (toAddQueue.isEmpty()) break
                    if (occupied.contains(x to y)) continue
                    val app = toAddQueue.removeFirst()
                    current.add(HomeItem(
                        key = app.key,
                        page = page,
                        cellX = x,
                        cellY = y,
                        type = HomeItem.TYPE_APP,
                        autoAdded = true
                    ))
                    occupied.add(x to y)
                }
                if (toAddQueue.isEmpty()) break
            }
            page++
            // Safety: non superare 50 pagine
            if (page > 50) break
        }

        store.save(current)
    }

    /**
     * v88: aggiunge una singola app appena installata al primo slot vuoto disponibile.
     * Se non c'è slot, crea nuova pagina. Marca come autoAdded così se l'utente
     * disabilita l'opzione "auto-add" può rimuoverla.
     */
    fun addSingleApp(store: HomeLayoutStore, appKey: String, gridCols: Int, gridRows: Int): Boolean {
        val current = store.load().toMutableList()
        val presentKeys = collectPresentKeys(current)
        if (presentKeys.contains(appKey)) return false  // già presente

        // Cerco primo slot vuoto in pagine esistenti
        val occupiedByPage = current.groupBy { it.page }
            .mapValues { (_, items) -> items.map { it.cellX to it.cellY }.toSet() }
        val maxPage = current.maxOfOrNull { it.page } ?: -1

        for (page in 0..(maxPage + 1).coerceAtMost(50)) {
            val occupied = occupiedByPage[page] ?: emptySet()
            for (y in 0 until gridRows) {
                for (x in 0 until gridCols) {
                    if (!occupied.contains(x to y)) {
                        current.add(HomeItem(
                            key = appKey,
                            page = page,
                            cellX = x,
                            cellY = y,
                            type = HomeItem.TYPE_APP,
                            autoAdded = true
                        ))
                        store.save(current)
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Rimuove tutti gli item con autoAdded=true. Chiamare quando l'utente riabilita il drawer.
     * Mantiene tutte le personalizzazioni manuali.
     */
    fun removeAutoAdded(store: HomeLayoutStore) {
        val current = store.load()
        val cleaned = current.filter { !it.autoAdded }
        if (cleaned.size != current.size) store.save(cleaned)
    }

    /**
     * Reset totale: cancella TUTTO il layout e ricrea con tutte le app alfabeticamente.
     * Chiamato dal bottone "Reset alla griglia automatica".
     */
    fun fullReset(store: HomeLayoutStore, gridCols: Int, gridRows: Int) {
        store.clear()
        populate(store, gridCols, gridRows)
    }

    /** Estrae tutte le app keys già presenti (slot diretti + dentro cartelle). */
    private fun collectPresentKeys(items: List<HomeItem>): Set<String> {
        val out = mutableSetOf<String>()
        for (item in items) {
            when (item.type) {
                HomeItem.TYPE_APP -> out.add(item.key)
                HomeItem.TYPE_FOLDER -> out.addAll(item.folderApps)
                // TYPE_TOOL non ha app key, ignoro
            }
        }
        return out
    }
}
