package com.axion.tempus.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axion.tempus.data.Note
import kotlinx.coroutines.launch
import kotlin.math.abs

/** ~1.4× prior 17sp body size */
private val NotesBodySp = 24.sp
private val NotesBodyLineHeight = 32.sp
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val latestNavigateToHome by rememberUpdatedState(onNavigateToHome)

    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var editorFocused by remember { mutableStateOf(false) }

    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = NotesTitleSp,
        lineHeight = NotesTitleLineHeight,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = NotesBodySp,
        lineHeight = NotesBodyLineHeight,
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF121212),
                modifier = Modifier.fillMaxWidth(0.76f)
            ) {
                Text(
                    text = "Notes",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = NotesTitleSp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(24.dp)
                )
                LazyColumn {
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
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectSwipeToHome {
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
                        .padding(horizontal = 16.dp)
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
