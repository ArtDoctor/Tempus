package com.axion.tempus.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axion.tempus.data.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Drawer list preview line (title snippet). */
private val NotesBodySp = 24.sp
private val NotesBodyLineHeight = 32.sp
/** Main note body text in the editor (below the title line). */
private val NotesEditorBodySp = 22.sp
private val NotesEditorBodyLineHeight = 29.sp
private val NotesHorizontalPadding = 28.dp
private val NotesTitleSp = 28.sp
private val NotesTitleLineHeight = 36.sp

@Composable
fun NotesScreen(
    onNavigateToHome: () -> Unit = {},
    viewModel: NotesViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val latestNavigateToHome by rememberUpdatedState(onNavigateToHome)

    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var editorFocused by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var copyFeedbackVisible by remember { mutableStateOf(false) }
    val copyIconScale by animateFloatAsState(
        targetValue = if (copyFeedbackVisible) 1.12f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "copyIconScale"
    )
    val copyButtonColor by animateColorAsState(
        targetValue = if (copyFeedbackVisible) Color(0xFF1E3A2D) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "copyButtonColor"
    )

    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = NotesTitleSp,
        lineHeight = NotesTitleLineHeight,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = NotesEditorBodySp,
        lineHeight = NotesEditorBodyLineHeight,
        color = Color.White
    )
    val titleSpan = titleStyle.toSpanStyle()
    val bodySpan = bodyStyle.toSpanStyle()

    LaunchedEffect(currentNote?.id) {
        val n = currentNote ?: return@LaunchedEffect
        val plain = combinedNoteText(n.title, n.body)
        textFieldValue = TextFieldValue(
            annotatedString = notesAnnotatedString(plain, titleSpan, bodySpan),
            selection = TextRange(plain.length),
            composition = null
        )
    }

    val placeholderAnnotated = remember(titleSpan) {
        notesPlaceholderAnnotated(titleSpan)
    }
    val paragraphVisualTransformation = remember(titleSpan, bodySpan) {
        VisualTransformation { text ->
            val plain = text.text
            val expanded = buildString(plain.length * 2) {
                plain.forEach { c ->
                    append(c)
                    if (c == '\n') append('\n')
                }
            }
            TransformedText(
                text = notesAnnotatedString(expanded, titleSpan, bodySpan),
                offsetMapping = newlinePairOffsetMapping(plain)
            )
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete notes") },
            text = { Text("Are you sure you want to delete your notes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllNotes()
                        scope.launch { drawerState.close() }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(copyFeedbackVisible) {
        if (copyFeedbackVisible) {
            delay(1400)
            copyFeedbackVisible = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF121212),
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight()
            ) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = "Notes",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = NotesTitleSp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(24.dp)
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allNotes, key = { it.id }) { note ->
                            NoteListRow(
                                note = note,
                                onClick = {
                                    viewModel.selectNote(note.id)
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete all notes",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(focusManager, keyboardController, latestNavigateToHome) {
                        detectSwipeToHome {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            latestNavigateToHome()
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(top = 56.dp)
                        .padding(horizontal = NotesHorizontalPadding)
                        .pointerInput(editorFocused) {
                            if (!editorFocused) {
                                detectTapGestures(
                                    onTap = {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                )
                            }
                        }
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        visualTransformation = paragraphVisualTransformation,
                        onValueChange = { new ->
                            val plain = new.text
                            textFieldValue = TextFieldValue(
                                annotatedString = notesAnnotatedString(plain, titleSpan, bodySpan),
                                selection = new.selection,
                                composition = new.composition
                            )
                            viewModel.scheduleSave(plain)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .onFocusChanged { editorFocused = it.isFocused },
                        cursorBrush = SolidColor(Color.White),
                        textStyle = bodyStyle,
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = placeholderAnnotated,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
                FloatingActionButton(
                    onClick = { viewModel.createNote() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(20.dp),
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New note")
                }
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "All notes",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(textFieldValue.text))
                        copyFeedbackVisible = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(copyButtonColor, RoundedCornerShape(50))
                        .scale(copyIconScale)
                ) {
                    Crossfade(
                        targetState = copyFeedbackVisible,
                        animationSpec = tween(durationMillis = 160),
                        label = "copyIconCrossfade"
                    ) { copied ->
                        Icon(
                            imageVector = if (copied) Icons.Filled.DoneAll else Icons.Filled.ContentCopy,
                            contentDescription = if (copied) "Note copied" else "Copy note",
                            tint = Color.White
                        )
                    }
                }
                AnimatedVisibility(
                    visible = copyFeedbackVisible,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    enter = fadeIn(tween(140)) +
                        slideInVertically(tween(180)) { -it / 2 } +
                        scaleIn(tween(180), initialScale = 0.92f),
                    exit = fadeOut(tween(160)) +
                        slideOutVertically(tween(180)) { -it / 3 } +
                        scaleOut(tween(160), targetScale = 0.96f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF1E3A2D), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Copied",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectSwipeToHome(
    onSwipeToHome: () -> Unit
) {
    val swipeThresholdPx = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val touchSlop = viewConfiguration.touchSlop
        var totalX = 0f
        var totalY = 0f
        var accepted = false
        val pointerId = down.id

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

            if (!change.pressed) {
                if (accepted && totalX < -swipeThresholdPx) {
                    onSwipeToHome()
                }
                break
            }

            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y

            if (!accepted) {
                val horizontalDistance = abs(totalX)
                val verticalDistance = abs(totalY)

                when {
                    horizontalDistance > touchSlop && horizontalDistance > verticalDistance -> {
                        if (totalX < 0f) {
                            accepted = true
                            change.consume()
                        } else {
                            break
                        }
                    }
                    verticalDistance > touchSlop && verticalDistance > horizontalDistance -> break
                }
            } else {
                if (delta.x != 0f) {
                    change.consume()
                    if (totalX < -swipeThresholdPx) {
                        onSwipeToHome()
                        break
                    }
                }
            }
        }
    }
}

/** Matches [NotesRepository.saveNoteText] line split: title = first line, body = rest. */
private fun combinedNoteText(title: String, body: String): String = when {
    title.isEmpty() && body.isEmpty() -> ""
    title.isEmpty() -> "\n$body"
    body.isEmpty() -> title
    else -> "$title\n$body"
}

private fun notesAnnotatedString(
    plain: String,
    titleSpan: SpanStyle,
    bodySpan: SpanStyle
): AnnotatedString = buildAnnotatedString {
    val nl = plain.indexOf('\n')
    if (nl == -1) {
        withStyle(titleSpan) { append(plain) }
    } else {
        withStyle(titleSpan) { append(plain.substring(0, nl)) }
        withStyle(bodySpan) { append(plain.substring(nl)) }
    }
}

/**
 * Pairs each `\n` in [plain] with an extra `\n` for display. [plain] is the stored text (one `\n`
 * per Enter); wrapped lines have no extra gap.
 */
private fun newlinePairOffsetMapping(plain: String): OffsetMapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, plain.length)
        return o + plain.substring(0, o).count { it == '\n' }
    }

    override fun transformedToOriginal(offset: Int): Int {
        val maxT = originalToTransformed(plain.length)
        val t = offset.coerceIn(0, maxT)
        var orig = 0
        var trans = 0
        while (orig < plain.length && trans < t) {
            if (plain[orig] == '\n') {
                val nextTrans = trans + 2
                if (nextTrans <= t) {
                    trans = nextTrans
                    orig++
                } else {
                    return (orig + 1).coerceAtMost(plain.length)
                }
            } else {
                trans++
                orig++
            }
        }
        return orig.coerceIn(0, plain.length)
    }
}

private fun notesPlaceholderAnnotated(titleSpan: SpanStyle): AnnotatedString = buildAnnotatedString {
    withStyle(titleSpan.copy(color = Color(0xFF666666))) { append("Title") }
}

@Composable
private fun NoteListRow(note: Note, onClick: () -> Unit) {
    val title = note.title.trim().ifBlank { "Empty note" }
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = NotesBodySp,
            lineHeight = NotesBodyLineHeight,
            fontWeight = FontWeight.Normal
        )
    )
}
