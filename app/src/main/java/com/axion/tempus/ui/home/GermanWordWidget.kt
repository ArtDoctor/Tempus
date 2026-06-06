package com.axion.tempus.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axion.tempus.data.GermanWord

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GermanWordWidget(
    word: GermanWord,
    visible: Boolean,
    onNextWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(90)),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = word,
            transitionSpec = {
                fadeIn(animationSpec = tween(160)) togetherWith
                    fadeOut(animationSpec = tween(90)) using
                    SizeTransform(clip = false)
            },
            label = "German word"
        ) { currentWord ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp)
                    .clickable(
                        onClickLabel = "Show next German word",
                        role = Role.Button,
                        onClick = onNextWord
                    )
                    .padding(vertical = 10.dp)
            ) {
                AccentLine(width = 28.dp)
                Text(
                    text = currentWord.german,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 30.sp,
                        lineHeight = 36.sp
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                )
                Separator(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = currentWord.translation,
                    color = Color(0xFFD2D2D2),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (currentWord.hasForms) {
                    Text(
                        text = currentWord.forms.joinToString(", "),
                        color = Color(0xFF777777),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            letterSpacing = 0.sp
                        ),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentLine(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Color(0xFF555555))
    )
}

@Composable
private fun Separator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(120.dp)
            .height(1.dp)
            .background(Color(0xFF2A2A2A))
    )
}
