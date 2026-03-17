package com.procyon.esp_rc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@Composable
fun JoyStick(modifier: Modifier = Modifier, density: Density = LocalDensity.current, size : Dp = 200.dp, xy: (Pair<Int, Int>) -> Unit) {

    val center by derivedStateOf { size.value * density.density / 2 }

    val thumbPosAnimatable = remember { Animatable(Offset(center,center), Offset.VectorConverter) }
    val thumbRadius = with(density){30.dp.toPx()}

    var draggable  by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
//    val thumbCenterBound = Rect(offset = Offset(thumbRadius, thumbRadius), size = Size((center - thumbRadius) *2 , (center - thumbRadius) * 2))
    val thumbCenterBound = Rect(center = Offset(center, center), radius = center - thumbRadius)


    LaunchedEffect(thumbPosAnimatable.value) {
        val pos = thumbPosAnimatable.value
        //y relative to center
        val yRelative = (((pos.y - center) / (center - thumbRadius)) * -100f).toInt()
        val xRelative = (((pos.x - center) / (center - thumbRadius)) * 100f).toInt()
        xy(Pair(xRelative, yRelative))


    }


    Canvas(modifier
        .size(size)
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { touchoffset ->
                    if ((touchoffset - thumbPosAnimatable.value).getDistance() <= thumbRadius) {
                        draggable = true
                        scope.launch {
                            thumbPosAnimatable.animateTo(
                                targetValue = touchoffset,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy
                                )
                            )
                        }
                    } else {
                        draggable = false
                    }

                },
                onDrag = { change, dragAmount ->
                    if (draggable) {
                        val absoluteCenter = Offset(
                            x = change.position.x.coerceIn(thumbRadius, (center * 2) - thumbRadius),
                            y = change.position.y.coerceIn(thumbRadius, (center * 2) - thumbRadius)
                        )


                        val adjustedCenter = absoluteCenter /*+ dragOffset*/
                        scope.launch {
                            thumbPosAnimatable.snapTo(adjustedCenter)
                        }
                    }

                },
                onDragEnd = {
                    scope.launch {
                        thumbPosAnimatable.animateTo(
                            targetValue = Offset(center, center),
                            animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy
                                )
                            )
                        }
                    }

                )
            }
        ) {
        //draw border
        drawCircle(color = Color.White, style = Stroke(width = 1.dp.toPx()))

        val centerOffset = Offset(center, center)
        val thumbVector: Offset = thumbPosAnimatable.value - centerOffset
        val shadowCenter = centerOffset + (thumbVector * 0.5f)
        //draw thumb shadow
        drawCircle(color = Color.DarkGray, radius = thumbRadius * 2, center = shadowCenter )
        //draw thumbPos
        drawCircle(color = Color.White, radius = thumbRadius, center = thumbPosAnimatable.value)

    }
    
}

@Preview
@Composable
private fun JoystickPreview() {
    var xRel by remember { mutableIntStateOf(0) }
    var yRel by remember { mutableIntStateOf(0) }
    JoyStick(xy = {
        xRel = it.first
        yRel = it.second
    })
    Text("X = $xRel; Y = $yRel")
    
}

@Composable
fun StatusLine(modifier: Modifier = Modifier, state: ConnectionsState) {
    val alpha by if (state.flashing) {
        val infiniteTransition = rememberInfiniteTransition(label = "flashingTransition")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing, delayMillis = 100),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alphaAnimation"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(state.color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = state.text, color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StatusLinePreview() {
    StatusLine(state = ConnectionsState.Connecting)
}