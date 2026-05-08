package org.cheipstudio.speedlauncher.data

/**
 * v16: HomeItem ora supporta sia app singole che cartelle.
 * - type = "app" → key contiene l'app key
 * - type = "folder" → key è l'ID folder (es. "f_1234"), name è il nome visualizzato,
 *   folderApps contiene le keys delle app dentro
 * v59: aggiunto type "tool" per pulsanti come memory cleaner.
 */
data class HomeItem(
    val key: String,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val type: String = TYPE_APP,
    val name: String = "",
    val folderApps: List<String> = emptyList()
) {
    companion object {
        const val TYPE_APP = "app"
        const val TYPE_FOLDER = "folder"
        const val TYPE_TOOL = "tool"

        // Tool subtypes (key contiene quale tool)
        const val TOOL_MEMORY_CLEANER = "tool_memory_cleaner"
    }
}
