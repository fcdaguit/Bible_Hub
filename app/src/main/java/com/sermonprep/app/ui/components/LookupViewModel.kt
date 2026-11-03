package com.sermonprep.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sermonprep.app.data.db.BibleVersionEntity
import com.sermonprep.app.data.db.SermonEntity
import com.sermonprep.app.data.db.VerseEntity
import com.sermonprep.app.data.repo.BibleRepository
import com.sermonprep.app.data.repo.MARK_BOOKMARK
import com.sermonprep.app.data.repo.MARK_FAVORITE
import com.sermonprep.app.data.repo.MarkRepository
import com.sermonprep.app.data.repo.SermonRepository
import com.sermonprep.app.data.repo.SettingsRepository
import com.sermonprep.app.domain.BibleBooks
import com.sermonprep.app.domain.BibleRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PopupProblem { NONE, NO_TRANSLATION, NOT_IN_TRANSLATION }

/** One chapter shown in the scrollable reading area of the popup. */
data class ChapterBlock(val book: Int, val chapter: Int, val verses: List<VerseEntity>)

data class PopupState(
    /** The reference that was tapped. Copy/share/bookmark/add-to-outline act on this. */
    val ref: BibleRef,
    val versionId: String?,
    val versionName: String?,
    /** Verses of [ref] only. */
    val verses: List<VerseEntity>,
    /** What the reader can scroll through: [ref]'s whole chapter, plus any chapters the user continued into. */
    val blocks: List<ChapterBlock>,
    val hasMore: Boolean,
    /** Installed translations, for the switcher. */
    val versions: List<BibleVersionEntity>,
    /** True when the popup is showing a translation other than the preferred one. */
    val isTemporary: Boolean,
    val bookmarked: Boolean,
    val favorite: Boolean,
    val problem: PopupProblem,
) {
    fun plainText(): String {
        val body = verses.joinToString("\n") { "${it.verse} ${it.text}" }
        val ver = versionName?.let { " ($it)" } ?: ""
        return "${ref.label()}$ver\n$body"
    }
}

private fun nextChapter(book: Int, chapter: Int): Pair<Int, Int>? {
    val b = BibleBooks.get(book) ?: return null
    return when {
        chapter < b.chapters -> book to chapter + 1
        book < 66 -> book + 1 to 1
        else -> null
    }
}

/**
 * Shared by every screen that can pop up a verse (editor, reader, search, assistant).
 * Verse text always comes from the selected translation unless the user switches translation inside
 * the popup, which is temporary until they choose "Make default".
 */
@HiltViewModel
class LookupViewModel @Inject constructor(
    private val bible: BibleRepository,
    private val settings: SettingsRepository,
    private val marks: MarkRepository,
    private val sermons: SermonRepository,
) : ViewModel() {

    var popup by mutableStateOf<PopupState?>(null)
        private set
    var showPicker by mutableStateOf(false)
        private set

    private var overrideVersionId: String? = null

    val sermonList: StateFlow<List<SermonEntity>> =
        sermons.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun show(ref: BibleRef) {
        viewModelScope.launch { popup = build(ref) }
    }

    fun dismiss() {
        popup = null
        showPicker = false
        overrideVersionId = null
    }

    private suspend fun build(ref: BibleRef): PopupState {
        val all = bible.versionsOnce()
        val preferredId = settings.versionId.first()?.takeIf { id -> all.any { it.id == id } } ?: all.firstOrNull()?.id
        val chosenId = overrideVersionId?.takeIf { id -> all.any { it.id == id } } ?: preferredId
        val v = all.firstOrNull { it.id == chosenId }
        val bm = marks.isMarked(MARK_BOOKMARK, ref)
        val fav = marks.isMarked(MARK_FAVORITE, ref)
        if (v == null) {
            return PopupState(ref, null, null, emptyList(), emptyList(), false, all, false, bm, fav, PopupProblem.NO_TRANSLATION)
        }
        val verses = bible.passage(v.id, ref)
        val chapter = bible.chapter(v.id, ref.book, ref.chapter)
        return PopupState(
            ref = ref,
            versionId = v.id,
            versionName = v.name,
            verses = verses,
            blocks = if (chapter.isEmpty()) emptyList() else listOf(ChapterBlock(ref.book, ref.chapter, chapter)),
            hasMore = chapter.isNotEmpty() && nextChapter(ref.book, ref.chapter) != null,
            versions = all,
            isTemporary = v.id != preferredId,
            bookmarked = bm,
            favorite = fav,
            problem = if (verses.isEmpty()) PopupProblem.NOT_IN_TRANSLATION else PopupProblem.NONE,
        )
    }

    /** Switch the translation shown in the popup (does not change the app-wide preference). */
    fun selectVersion(id: String) {
        val p = popup ?: return
        overrideVersionId = id
        viewModelScope.launch { popup = build(p.ref) }
    }

    /** Make the translation currently shown in the popup the app-wide preferred one. */
    fun makeDefault() {
        val p = popup ?: return
        val id = p.versionId ?: return
        viewModelScope.launch {
            settings.setVersion(id)
            overrideVersionId = null
            popup = build(p.ref)
        }
    }

    /** Appends the next chapter to the scrollable area so the user can keep reading. */
    fun continueReading() {
        val p = popup ?: return
        val vid = p.versionId ?: return
        val last = p.blocks.lastOrNull() ?: return
        val (b, c) = nextChapter(last.book, last.chapter) ?: return
        viewModelScope.launch {
            val verses = bible.chapter(vid, b, c)
            val current = popup?.takeIf { it.ref == p.ref && it.versionId == vid } ?: return@launch
            popup = if (verses.isEmpty()) current.copy(hasMore = false)
            else current.copy(
                blocks = current.blocks + ChapterBlock(b, c, verses),
                hasMore = nextChapter(b, c) != null,
            )
        }
    }

    /** Verse-by-verse navigation. Moves the highlighted verse one step and rolls over chapter boundaries. */
    fun step(delta: Int) {
        val p = popup ?: return
        val vs = p.ref.verseStart ?: return
        val vid = p.versionId ?: return
        viewModelScope.launch {
            var book = p.ref.book
            var chapter = p.ref.chapter
            var verse = vs + delta
            val count = bible.verseCount(vid, book, chapter)
            if (verse > count) {
                if (chapter < (BibleBooks.get(book)?.chapters ?: chapter)) { chapter++; verse = 1 }
                else if (book < 66) { book++; chapter = 1; verse = 1 }
                else return@launch
            } else if (verse < 1) {
                if (chapter > 1) { chapter--; verse = bible.verseCount(vid, book, chapter) }
                else if (book > 1) {
                    book--; chapter = BibleBooks.get(book)?.chapters ?: 1
                    verse = bible.verseCount(vid, book, chapter)
                } else return@launch
            }
            if (verse >= 1) popup = build(BibleRef(book, chapter, verse))
        }
    }

    private fun toggle(type: String) {
        val p = popup ?: return
        viewModelScope.launch {
            marks.toggle(type, p.ref)
            // Update only the flags so the user's scroll position and extra chapters are kept.
            popup = popup?.copy(
                bookmarked = marks.isMarked(MARK_BOOKMARK, p.ref),
                favorite = marks.isMarked(MARK_FAVORITE, p.ref),
            )
        }
    }

    fun toggleBookmark() = toggle(MARK_BOOKMARK)
    fun toggleFavorite() = toggle(MARK_FAVORITE)

    /** "Add to outline" straight from a list row: load the passage, then ask which sermon. */
    fun addToOutline(ref: BibleRef) {
        viewModelScope.launch {
            popup = build(ref)
            showPicker = true
        }
    }

    fun openPicker() { showPicker = true }
    fun closePicker() { showPicker = false }

    private fun outlineLine(p: PopupState): String {
        val ver = p.versionName?.let { " ($it)" } ?: ""
        return "${p.ref.label()}$ver — " + p.verses.joinToString(" ") { it.text }
    }

    fun appendTo(sermonId: Long, onDone: (Long) -> Unit = {}) {
        val p = popup ?: return
        viewModelScope.launch {
            sermons.append(sermonId, outlineLine(p))
            showPicker = false
            onDone(sermonId)
        }
    }

    fun appendToNew(onDone: (Long) -> Unit = {}) {
        val p = popup ?: return
        viewModelScope.launch {
            val id = sermons.create(title = p.ref.label(), content = outlineLine(p))
            showPicker = false
            onDone(id)
        }
    }
}
