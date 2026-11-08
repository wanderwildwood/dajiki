package com.wanderwildwood.dajiki.write

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.system.ErrnoException
import android.system.Os
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One file in the folder: what it is called, where it is, and when it last changed. [diary] is
 * true for a sheet in the Diary folder inside the chosen one, which is only read while "Keep the
 * diary in its own folder" is on.
 */
data class Sheet(val uri: Uri, val name: String, val modified: Long, val diary: Boolean = false)

/** The folder inside the chosen one that the diary is kept in when it has a folder of its own. */
const val DIARY_FOLDER = "Diary"

/**
 * Whether [sheet] belongs to the diary: it is in the Diary folder, or it is a day's page (the
 * date alone for a name) wherever it is. What the diary lock covers.
 */
fun isDiaryPage(sheet: Sheet): Boolean = isDiaryPage(sheet.name, sheet.diary)

/** [isDiaryPage] by the sheet's name and whether it is in the Diary folder. */
fun isDiaryPage(name: String, inDiaryFolder: Boolean): Boolean =
    inDiaryFolder || dayInName(name)?.let { isPageFor(name, it) } == true

/**
 * What the Sheets tab lists: everything, or with the diary in its own folder, everything but
 * the diary, which By day and Search still show.
 */
fun sheetsTab(sheets: List<Sheet>, diaryFolder: Boolean): List<Sheet> = sheetsTab(sheets, diaryFolder) { it.diary }

/** [sheetsTab] over anything that can say whether it is in the Diary folder. */
fun <T> sheetsTab(sheets: List<T>, diaryFolder: Boolean, inDiaryFolder: (T) -> Boolean): List<T> =
    if (diaryFolder) sheets.filterNot(inDiaryFolder) else sheets

/**
 * What a sheet looked like from the outside at a moment: when the provider said it last
 * changed, and how long it was. Either may be missing, and the provider says so with a
 * number that is not a real one.
 */
data class Stamp(val modified: Long, val size: Long)

/**
 * Whether the sheet changed between two stamps, or null where the stamps cannot say.
 *
 * Kept out of [Folder] and away from anything Android so the rule itself can be tested,
 * because it is the rule that decides whether somebody's afternoon gets overwritten.
 *
 * A different length is a change, and that holds even from a provider that dates nothing.
 * A different date is a change. The same length with no usable dates on either side is the
 * one case this cannot answer, and it says so rather than guessing "unchanged", which is
 * the guess that loses work.
 */
fun changedBetween(before: Stamp, now: Stamp): Boolean? = when {
    before.size >= 0 && now.size >= 0 && before.size != now.size -> true
    before.modified > 0 && now.modified > 0 -> before.modified != now.modified
    else -> null
}

/**
 * Whether a sheet that was there ([before]) is no longer there ([now] is null). A sheet this
 * app never saw from outside has nothing to have vanished from.
 */
fun vanished(before: Stamp?, now: Stamp?): Boolean = before != null && now == null

/**
 * Whether a file in the folder is something this app should offer to open.
 *
 * The extension catches a sheet a sync app has put there with no type on it at all, which is
 * common; the type catches one whose name carries no extension, which is what several
 * providers do. The third case is a name with no extension that starts with a date: this
 * app's own sheets, made before 0.1.37 by a provider that neither put `.txt` on the name nor
 * typed the file as text (Cabinet's folder picker does neither), and so dropped from the list
 * although they were sitting in the folder.
 */
fun isWriting(name: String, mime: String?): Boolean {
    if (mime == DIR_MIME) return false
    val lower = name.lowercase(Locale.ROOT)
    val known = lower.endsWith(".txt") || lower.endsWith(".md") ||
        lower.endsWith(".markdown") || lower.endsWith(".text")
    if (known || mime?.startsWith("text/") == true) return true
    return '.' !in name && dayInName(name) != null
}

/**
 * The name a new sheet should end up with, given what the provider called it ([got]), or null
 * where [got] already ends in `.extension`. A provider is asked for the full name, and some
 * still drop or swap the end of it; the provider's own "(1)" for a name already taken stays.
 */
fun withExtension(got: String, extension: String): String? {
    if (got.lowercase(Locale.ROOT).endsWith(".$extension")) return null
    return "$got.$extension"
}

/** [DocumentsContract.Document.MIME_TYPE_DIR], spelled out so the rules above need no Android. */
private const val DIR_MIME = "vnd.android.document/directory"

/**
 * What to call the copy holding what was typed here, when the sheet itself has moved on.
 *
 * The extension is kept where there is one, so a `.md` that came from a laptop forks to a
 * `.md` rather than to something the reader's other machine will not recognise.
 */
fun besideName(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) {
        "${name.substring(0, dot)} (this phone)${name.substring(dot)}"
    } else {
        "$name (this phone)"
    }
}

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
    fun list(folder: Uri, diary: Boolean = false): List<Sheet> {
        val sheets = listIn(folder, DocumentsContract.getTreeDocumentId(folder), diary = false)
        val inDiary = if (diary) diaryFolder(folder, make = false)?.let { listIn(folder, it, diary = true) } else null
        return (sheets + inDiary.orEmpty()).sortedByDescending { it.modified }
    }

    /**
     * The document id of the Diary folder inside [folder], made first if [make] and there is
     * none, or null. Found by name each time rather than remembered: it may have been renamed,
     * deleted or brought in by a sync app since.
     */
    fun diaryFolder(folder: Uri, make: Boolean): String? {
        val root = DocumentsContract.getTreeDocumentId(folder)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, root)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == DIARY_FOLDER && cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return cursor.getString(0)
                }
            }
        }
        if (!make) return null
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, root)
        val made = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, DIARY_FOLDER)
            ?: error("The folder would not take a Diary folder.")
        return DocumentsContract.getDocumentId(made)
    }

    private fun listIn(folder: Uri, parent: String, diary: Boolean): List<Sheet> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, parent)
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
                    diary = diary,
                )
            }
        }
        return sheets
    }

    /**
     * How the sheet at [uri] looks from the outside right now, or null if it is not there.
     *
     * Asked immediately before a write, and recorded again immediately after one, which is
     * what lets a sheet that moved on underneath us be told apart from one this app last
     * wrote itself.
     */
    fun stamp(uri: Uri): Stamp? = try {
        stampOf(uri)
    } catch (_: Exception) {
        // A provider that answers for a missing document by throwing rather than by an empty
        // cursor (the storage provider does). Same answer: it is not there.
        null
    }

    private fun stampOf(uri: Uri): Stamp? =
        resolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use {
            if (!it.moveToFirst()) {
                null
            } else {
                Stamp(
                    modified = if (it.isNull(0)) 0L else it.getLong(0),
                    size = if (it.isNull(1)) -1L else it.getLong(1),
                )
            }
        }

    /** What the provider calls the document at [uri], or null if it will not say. */
    private fun nameOf(uri: Uri): String? =
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /**
     * Rename the sheet at [uri], keeping its extension when the reader did not type one.
     *
     * The provider hands back a new address, because on most of them the document id
     * carries the name. Everything holding the old one has to be told, which is why this
     * returns the sheet rather than a success flag.
     */
    fun rename(sheet: Sheet, wanted: String): Sheet? {
        val uri = sheet.uri
        val was = sheet.name
        val trimmed = wanted.trim()
        if (trimmed.isEmpty()) return null
        val extension = was.substringAfterLast('.', "")
        val name = if (extension.isNotEmpty() && !trimmed.contains('.')) "$trimmed.$extension" else trimmed
        if (name == was) return null
        val moved = DocumentsContract.renameDocument(resolver, uri, name) ?: return null
        return Sheet(uri = moved, name = nameOf(moved) ?: name, modified = System.currentTimeMillis(), diary = sheet.diary)
    }

    /** Remove the sheet. Throws rather than returning false quietly if the provider refuses. */
    fun delete(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) {
            error("The folder would not delete it.")
        }
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
     * A new sheet beside an existing one, under a name this app chose.
     *
     * Used for one thing: holding what was typed here when the sheet itself has changed
     * somewhere else. The type is worked out from the name so that a `.md` forked from a
     * laptop stays a `.md`; where the phone does not know the extension this falls back to
     * plain text, and the provider may then add `.txt` on the end of it. An ugly name is an
     * acceptable price for not having to guess which copy to throw away.
     */
    fun createBeside(folder: Uri, name: String, diary: Boolean = false): Sheet {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            folder,
            (if (diary) diaryFolder(folder, make = true) else null) ?: DocumentsContract.getTreeDocumentId(folder),
        )
        val uri = DocumentsContract.createDocument(resolver, parent, mimeFor(name), name)
            ?: error("The folder would not take a second copy.")
        return Sheet(uri = uri, name = nameOf(uri) ?: name, modified = System.currentTimeMillis(), diary = diary)
    }

    private fun mimeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "text/plain"
    }

    /**
     * A new sheet, named for the moment it was started.
     *
     * The name is a date rather than something the reader typed. A sheet has to exist before
     * there is anywhere to put the first sentence, and a dialog between wanting to write and
     * writing is the wrong thing to put there — particularly on a panel where a dialog costs
     * two full repaints. The date sorts, and a sheet can be renamed from any file manager.
     *
     * Plain text asks the provider for `text/plain` under the full name, `.txt` and all, and
     * puts the `.txt` back on if the provider left it off. Markdown cannot be asked for that way: a name ending `.md` against a plain-text type is how
     * files called `notes.md.txt` happen, and Android's own storage does not count `.md` as
     * belonging to any text type it knows. So a `.md` is asked for by its full name against
     * no type at all, the way Notes makes its own, and the name is put back if the provider
     * changed it anyway. Markdown in a `.txt` is still markdown, and a `.md` put in the folder
     * from elsewhere opens here perfectly well whichever is set.
     */
    fun create(
        folder: Uri,
        format: Format,
        // Today's page passes its own name, the date alone; everything else is named for the minute.
        stamp: String = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.ROOT).format(Date()),
        // Today's page, with the diary in its own folder: made in the Diary folder.
        diary: Boolean = false,
    ): Sheet {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            folder,
            (if (diary) diaryFolder(folder, make = true) else null) ?: DocumentsContract.getTreeDocumentId(folder),
        )
        var uri: Uri
        if (format == Format.PLAIN) {
            // Asked for by its full name. Android's own storage keeps a `.txt` it is given
            // against `text/plain` and adds one only where the name lacks it, but not every
            // provider adds one: Cabinet's folder picker makes the file under exactly the name
            // it is handed, so a bare date came out as a file with no extension and no text
            // type, which the list then passed over (t/16264).
            uri = DocumentsContract.createDocument(resolver, parent, "text/plain", "$stamp.${format.extension}")
                ?: error("The folder would not take a new sheet.")
            val got = nameOf(uri)
            val fixed = got?.let { withExtension(it, format.extension) }
            if (fixed != null) {
                uri = runCatching { DocumentsContract.renameDocument(resolver, uri, fixed) }.getOrNull() ?: uri
            }
        } else {
            val wanted = "$stamp.${format.extension}"
            uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", wanted)
                ?: error("The folder would not take a new sheet.")
            // "(1)" for a second sheet in the same minute is the provider's and stays; an
            // extension it added or swapped is not.
            val got = nameOf(uri)
            if (got != null && !got.endsWith(".${format.extension}")) {
                uri = runCatching { DocumentsContract.renameDocument(resolver, uri, wanted) }.getOrNull() ?: uri
            }
        }
        // What it ended up called, asked rather than assumed. The provider adds the
        // extension, and where a sheet of that name is already there it picks another --
        // "2026-09-17 1432 (1).txt" -- and the row would otherwise name a file that is not
        // the one just made.
        return Sheet(
            uri = uri,
            name = nameOf(uri) ?: "$stamp.${format.extension}",
            modified = System.currentTimeMillis(),
            diary = diary,
        )
    }
}
