package com.wanderwildwood.dajiki.write

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.ErrnoException
import android.system.Os
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One file in the folder: what it is called, where it is, and when it last changed. */
data class Sheet(val uri: Uri, val name: String, val modified: Long)

/**
 * The folder the reader chose, and the sheets in it.
 *
 * Everything goes through the document provider rather than a path. That is what lets the
 * folder be one a sync app owns, and it is why this app asks for no storage permission: the
 * grant the picker hands back covers that folder and nothing else.
 */
class Folder(context: Context) {

    private val resolver = context.contentResolver

    /**
     * Every sheet in the folder, newest first.
     *
     * Queried in one go rather than through `DocumentFile.listFiles()`, which looks
     * respectable and then asks the provider again for every single name, date and type. On
     * a folder of any size, on the processors these panels are attached to, that is the
     * difference between a list that appears and a list you wait for.
     */
    fun list(folder: Uri): List<Sheet> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder,
            DocumentsContract.getTreeDocumentId(folder),
        )
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        val sheets = mutableListOf<Sheet>()
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                if (!isWriting(name, mime)) continue
                sheets += Sheet(
                    uri = DocumentsContract.buildDocumentUriUsingTree(folder, id),
                    name = name,
                    modified = cursor.getLong(3),
                )
            }
        }
        return sheets.sortedByDescending { it.modified }
    }

    /**
     * Whether a file in the folder is something this app should offer to open.
     *
     * Both halves are needed. The extension catches a sheet a sync app has put there with no
     * type on it at all, which is common; the type catches one whose name carries no
     * extension, which is what several providers do.
     */
    private fun isWriting(name: String, mime: String?): Boolean {
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) return false
        val lower = name.lowercase(Locale.ROOT)
        val known = lower.endsWith(".txt") || lower.endsWith(".md") ||
            lower.endsWith(".markdown") || lower.endsWith(".text")
        return known || mime?.startsWith("text/") == true
    }

    /** Everything in a sheet, as text. */
    fun read(uri: Uri): String =
        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("The folder would not open ${uri.lastPathSegment}.")

    /**
     * Put [text] in the sheet at [uri], and leave nothing of the old one behind it.
     *
     * The truncation is the whole of the care here. `openOutputStream(uri, "w")` does not
     * shorten the file on a good many providers: it writes the new text over the start of the
     * old and leaves whatever ran past the end still sitting there, so cutting a paragraph
     * and saving gives the reader their new document with the tail of the previous one welded
     * onto it. The "t" asks the provider to truncate, and `ftruncate` afterwards settles it
     * for any provider that quietly ignored the ask.
     */
    fun write(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "rwt") }
            .getOrNull()
            ?: resolver.openFileDescriptor(uri, "rw")
            ?: error("The folder would not let ${uri.lastPathSegment} be written.")

        descriptor.use {
            FileOutputStream(it.fileDescriptor).use { stream ->
                stream.write(bytes)
                stream.flush()
                // Inside the stream's block deliberately: closing a FileOutputStream built
                // on a descriptor closes the descriptor too, and ftruncate on a closed one
                // fails with EBADF.
                try {
                    Os.ftruncate(it.fileDescriptor, bytes.size.toLong())
                } catch (_: ErrnoException) {
                    // A provider whose descriptor cannot be truncated in place. If "rwt" was
                    // honoured the file was already truncated when it opened, so this is a
                    // second line of defence failing rather than the save failing, and it is
                    // not worth telling the reader about.
                }
            }
        }
    }

    /**
     * A new sheet, named for the moment it was started.
     *
     * The name is a date rather than something the reader typed. A sheet has to exist before
     * there is anywhere to put the first sentence, and a dialog between wanting to write and
     * writing is the wrong thing to put there — particularly on a panel where a dialog costs
     * two full repaints. The date sorts, and a sheet can be renamed from any file manager.
     *
     * `.txt` regardless of what the reader intends to write in it: asking a provider for a
     * name ending `.md` against a plain-text type is how files called `notes.md.txt` happen.
     * Markdown in a `.txt` is still markdown, and a `.md` put in the folder from elsewhere
     * opens here perfectly well.
     */
    fun create(folder: Uri): Sheet {
        val name = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.ROOT).format(Date())
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            folder,
            DocumentsContract.getTreeDocumentId(folder),
        )
        val uri = DocumentsContract.createDocument(resolver, parent, "text/plain", name)
            ?: error("The folder would not take a new sheet.")
        return Sheet(uri = uri, name = "$name.txt", modified = System.currentTimeMillis())
    }
}
