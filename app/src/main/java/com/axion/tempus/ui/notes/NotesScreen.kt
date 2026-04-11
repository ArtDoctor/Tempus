package com.axion.tempus.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axion.tempus.data.Note
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(
    onNavigateToHome: () -> Unit = {},
    viewModel: NotesViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(currentNote?.id) {
        val n = currentNote ?: return@LaunchedEffect
        val combined = noteToCombinedText(n)
        textFieldValue = TextFieldValue(
            annotatedString = annotateNoteContent(combined),
            selection = TextRange(combined.length)
        )
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
                    style = MaterialTheme.typography.titleLarge,
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
            val edgeWidth = maxOf(72.dp, maxWidth * 0.22f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(top = 56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                BasicTextField(
                    value = TextFieldValue(
                        annotatedString = annotateNoteContent(textFieldValue.text),
                        selection = textFieldValue.selection,
                        composition = textFieldValue.composition
                    ),
                    onValueChange = { new ->
                        textFieldValue = TextFieldValue(
                            annotatedString = annotateNoteContent(new.text),
                            selection = new.selection,
                            composition = new.composition
                        )
                        viewModel.scheduleSave(new.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    cursorBrush = SolidColor(Color.White),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        color = Color.White
                    ),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (textFieldValue.text.isEmpty()) {
                                Text("Note", color = Color(0xFF666666), fontSize = 22.sp)
                            }
                            inner()
                        }
                    }
                )
            }

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(edgeWidth)
                    .pointerInput(swipeThresholdPx) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                // Swipe right from left edge opens drawer (positive dragAmount).
                                if (total > swipeThresholdPx) {
                                    scope.launch { drawerState.open() }
                                }
                                total = 0f
                            }
                        )
                    }
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(edgeWidth)
                    .pointerInput(swipeThresholdPx, onNavigateToHome) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                // Swipe left toward home (negative dragAmount), same as HorizontalPager.
                                if (total < -swipeThresholdPx) {
                                    onNavigateToHome()
                                }
                                total = 0f
                            }
                        )
                    }
            )
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

private fun noteToCombinedText(n: Note): String = buildString {
    append(n.title)
    if (n.title.isNotEmpty() && n.body.isNotEmpty()) append('\n')
    append(n.body)
}

private fun annotateNoteContent(plain: String) = buildAnnotatedString {
    val titleStyle = SpanStyle(
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )
    val bodyStyle = SpanStyle(
        color = Color(0xFFE0E0E0),
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal
    )
    val nl = plain.indexOf('\n')
    if (nl == -1) {
        withStyle(titleStyle) { append(plain) }
    } else {
        withStyle(titleStyle) { append(plain.substring(0, nl)) }
        withStyle(ParagraphStyle(lineHeight = 26.sp)) {
            withStyle(bodyStyle) { append(plain.substring(nl)) }
        }
    }
}

@Composable
private fun NoteListRow(note: Note, onClick: () -> Unit) {
    val preview = note.title.trim().ifBlank {
        note.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Empty note"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = preview,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
