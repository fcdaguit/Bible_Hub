package com.sermonprep.app.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sermonprep.app.R
import com.sermonprep.app.data.db.VerseEntity
import com.sermonprep.app.domain.BibleBooks
import com.sermonprep.app.domain.BibleRef

fun shareText(ctx: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

fun copyText(ctx: android.content.Context, clipboard: androidx.compose.ui.platform.ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
    Toast.makeText(ctx, ctx.getString(R.string.verse_copied), Toast.LENGTH_SHORT).show()
}

/** A line in the popup's scrollable reading area. */
private sealed interface ReadRow {
    data class Header(val text: String) : ReadRow
    data class Verse(val verse: VerseEntity, val highlighted: Boolean) : ReadRow
}

private fun buildRows(state: PopupState): List<ReadRow> = buildList {
    val ref = state.ref
    state.blocks.forEachIndexed { i, block ->
        if (i > 0) add(ReadRow.Header("${BibleBooks.name(block.book)} ${block.chapter}"))
        block.verses.forEach { v ->
            val hit = i == 0 && ref.verseStart != null &&
                v.verse in ref.verseStart..(ref.verseEnd ?: ref.verseStart)
            add(ReadRow.Verse(v, hit))
        }
    }
}

/**
 * The floating verse window. Place once per screen; it appears whenever [LookupViewModel.show]
 * is called (tapping a reference, a verse, a search result, ...).
 *
 * The tapped verses are highlighted inside a scrollable view of their whole chapter, and
 * "Continue reading" appends the following chapter. The header lets the user switch translation.
 */
@Composable
fun VerseLookupHost(
    vm: LookupViewModel,
    onOpenChapter: (BibleRef) -> Unit,
    onOpenSermon: ((Long) -> Unit)? = null,
) {
    val state = vm.popup ?: return
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var versionMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = vm::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                // Title row with verse-by-verse stepping
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.ref.verseStart != null) {
                        IconButton(onClick = { vm.step(-1) }) {
                            Icon(Icons.Filled.ChevronLeft, stringResource(R.string.action_previous))
                        }
                    }
                    Text(
                        state.ref.label(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.ref.verseStart != null) {
                        IconButton(onClick = { vm.step(1) }) {
                            Icon(Icons.Filled.ChevronRight, stringResource(R.string.action_next))
                        }
                    }
                    IconButton(onClick = vm::dismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                }

                // Translation switcher
                if (state.versions.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { versionMenu = true }, enabled = state.versions.size > 1) {
                                Text(
                                    state.versionName ?: stringResource(R.string.popup_change_translation),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (state.versions.size > 1) Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.popup_change_translation))
                            }
                            DropdownMenu(expanded = versionMenu, onDismissRequest = { versionMenu = false }) {
                                state.versions.forEach { v ->
                                    DropdownMenuItem(
                                        text = { Text(v.name) },
                                        leadingIcon = if (v.id == state.versionId) {
                                            { Icon(Icons.Filled.Check, null) }
                                        } else null,
                                        onClick = { versionMenu = false; vm.selectVersion(v.id) },
                                    )
                                }
                            }
                        }
                        if (state.isTemporary) {
                            TextButton(onClick = vm::makeDefault) { Text(stringResource(R.string.popup_make_default)) }
                        }
                    }
                }

                Spacer(Modifier.padding(2.dp))

                when (state.problem) {
                    PopupProblem.NO_TRANSLATION -> Text(stringResource(R.string.verse_no_translation))
                    PopupProblem.NOT_IN_TRANSLATION -> Text(stringResource(R.string.verse_not_found))
                    PopupProblem.NONE -> ReadingArea(state, onContinue = vm::continueReading)
                }

                if (state.problem == PopupProblem.NONE) {
                    Spacer(Modifier.padding(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { copyText(ctx, clipboard, state.plainText()) }) {
                            Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy))
                        }
                        IconButton(onClick = { shareText(ctx, state.plainText()) }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.action_share))
                        }
                        IconButton(onClick = vm::toggleBookmark) {
                            Icon(
                                if (state.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                stringResource(R.string.verse_bookmark),
                            )
                        }
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(
                                if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.verse_favorite),
                            )
                        }
                        IconButton(onClick = vm::openPicker) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, stringResource(R.string.verse_add_to_outline))
                        }
                        IconButton(onClick = { vm.dismiss(); onOpenChapter(state.ref) }) {
                            Icon(Icons.Filled.MenuBook, stringResource(R.string.verse_open_chapter))
                        }
                    }
                }
            }
        }
    }

    if (vm.showPicker) SermonPicker(vm, onOpenSermon)
}

@Composable
private fun ReadingArea(state: PopupState, onContinue: () -> Unit) {
    val rows = remember(state.blocks, state.ref) { buildRows(state) }
    val listState = rememberLazyListState()

    // Start with the tapped verse in view (one verse of lead-in above it). Re-run only when the
    // reference or translation changes, not when more chapters are appended.
    LaunchedEffect(state.ref, state.versionId) {
        val first = rows.indexOfFirst { it is ReadRow.Verse && it.highlighted }
        listState.scrollToItem(if (first > 0) first - 1 else 0)
    }

    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 340.dp)) {
        items(rows) { row ->
            when (row) {
                is ReadRow.Header -> Text(
                    row.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
                is ReadRow.Verse -> Text(
                    "${row.verse.verse}  ${row.verse.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (row.highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                            else Modifier
                        )
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                )
            }
        }
        if (state.hasMore) item {
            TextButton(onClick = onContinue, modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.popup_continue))
                Icon(Icons.Filled.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun SermonPicker(vm: LookupViewModel, onOpenSermon: ((Long) -> Unit)?) {
    val ctx = LocalContext.current
    val sermons by vm.sermonList.collectAsState()
    val done: (Long) -> Unit = {
        Toast.makeText(ctx, ctx.getString(R.string.added_to_outline), Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = vm::closePicker,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.picker_new)) },
                        leadingContent = { Icon(Icons.Filled.Add, null) },
                        modifier = Modifier.clickable { vm.appendToNew(done) },
                    )
                    HorizontalDivider()
                }
                items(sermons, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.title.ifBlank { stringResource(R.string.sermons_untitled) }) },
                        modifier = Modifier.clickable { vm.appendTo(s.id, done) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closePicker) { Text(stringResource(R.string.action_cancel)) } },
    )
}
