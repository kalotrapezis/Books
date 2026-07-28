package com.kalotrapezis.books.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** What the app writes, and the first thing it looks for when reading a book's folder. */
const val SYNC_FILE_NAME = "annotations.json"

/**
 * One folder per book inside a folder you already sync (Syncthing, Nextcloud):
 *
 * ```text
 * <the folder you pick>/
 *   The Brothers Karamazov/
 *     annotations.json
 *   Πληροφορική Α΄ Δημοτικού/
 *     annotations.json
 * ```
 *
 * The names are the books' own titles so a file can be dropped in from a desktop by
 * hand. The app writes `annotations.json`; when it reads, any `.json` in the folder
 * will do, so an export named something else still arrives. No server: Syncthing moves
 * the files, this only decides where they sit.
 */
object SyncFolder {
    /** The picked folder as a tree, or null when the permission no longer holds. */
    fun root(context: Context, uri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory && it.canRead() }

    /** The book's folder, made if it is not there yet. Null if the tree is unusable. */
    fun bookFolder(tree: DocumentFile, name: String): DocumentFile? {
        if (!tree.isDirectory) return null
        val wanted = folderName(name)
        val existing = tree.findFile(wanted)
        return when {
            existing?.isDirectory == true -> existing
            existing != null -> null // a file of that name is in the way
            else -> tree.createDirectory(wanted)
        }
    }

    /**
     * What to import: the newest `.json` in the folder, whatever it is called. Newest
     * rather than "ours first", because the point of the folder is that a file dropped
     * in from a desktop wins over what the app wrote last week.
     */
    fun readable(folder: DocumentFile): DocumentFile? = folder.listFiles()
        .filter {
            it.isFile && it.length() > 0 && it.name?.endsWith(".json", ignoreCase = true) == true
        }
        .maxByOrNull { it.lastModified() }

    /**
     * The file the app writes to, made if it is not there yet. The name is given
     * without its extension because providers append one for the type — passing
     * `annotations.json` here yields `annotations.json.json`.
     */
    fun writable(folder: DocumentFile): DocumentFile? {
        folder.findFile(SYNC_FILE_NAME)?.takeIf { it.isFile }?.let { return it }
        val made = folder.createFile("application/json", SYNC_FILE_NAME.removeSuffix(".json"))
            ?: return null
        if (made.name != SYNC_FILE_NAME) runCatching { made.renameTo(SYNC_FILE_NAME) }
        return folder.findFile(SYNC_FILE_NAME)?.takeIf { it.isFile } ?: made
    }

    /**
     * A title as a folder name: no separators, no characters Windows or Android refuse,
     * no leading dot, and short enough for any filesystem. Two books with the same title
     * share a folder — the import already warns when the identifier does not match.
     */
    fun folderName(title: String): String {
        val illegal = "\\/:*?\"<>|"
        val cleaned = buildString {
            for (character in title.trim()) {
                val safe = if (character in illegal || character.isISOControl()) ' ' else character
                if (safe == ' ' && (isEmpty() || last() == ' ')) continue
                append(safe)
            }
        }.trim(' ', '.').take(80)
        return cleaned.ifBlank { "Untitled book" }
    }
}
