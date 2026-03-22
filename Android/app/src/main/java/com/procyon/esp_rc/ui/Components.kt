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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.procyon.esp_rc.R
import kotlinx.coroutines.delay
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
            detectDragGestures(onDragStart = { touchoffset ->
                if ((touchoffset - thumbPosAnimatable.value).getDistance() <= thumbRadius) {
                    draggable = true
                    scope.launch {
                        thumbPosAnimatable.animateTo(
                            targetValue = touchoffset, animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        )
                    }
                } else {
                    draggable = false
                }

            }, onDrag = { change, dragAmount ->
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

            }, onDragEnd = {
                scope.launch {
                    thumbPosAnimatable.animateTo(
                        targetValue = Offset(center, center), animationSpec = spring(
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XTrim(modifier: Modifier = Modifier, trimIncrement : Int = 1, trim: Int, updateTrim: (Int) -> Unit) {

    Row(modifier
        .fillMaxWidth(0.8f)
        .height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {

        ContinuesPressFab(action = { updateTrim(trim - trimIncrement)}) {
            Icon(painter = painterResource(R.drawable.remove_24px),
                contentDescription = "subtract")
        }

        Spacer(modifier = Modifier.width(8.dp),)

        val progressBarWidth = with(LocalDensity.current){ 2.toDp().toPx() }
        val progressBarCircleRadius = with(LocalDensity.current){ 58.toDp().toPx() }

        val textMeasures = rememberTextMeasurer()


        Canvas(modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),) {
            drawLine(color = Color.White,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = progressBarWidth)




            val xPos = center.x + ((trim.coerceIn(-100..100) * (center.x / 2)) / (center.x / 2))
            val centerOffset = Offset(xPos, center.y)

            val textMeasureResult = textMeasures.measure(text = "$trim",
                style = TextStyle.Default.copy(color = Color.Black, fontSize = 22.sp))
            val textSize = textMeasureResult.size

            drawCircle(Color.White,
                radius = progressBarCircleRadius,
                center = centerOffset)

            drawText(textMeasureResult, topLeft = centerOffset.minus(Offset(textSize.width / 2f, textSize.height / 2f)),
                )
        }

        Spacer(modifier = Modifier.width(8.dp))

        ContinuesPressFab(modifier = Modifier, action = { updateTrim(trim + trimIncrement) }){
            Icon(Icons.Default.Add,
                contentDescription = "add")
        }
    }

}

@Composable
fun ContinuesPressFab(modifier: Modifier = Modifier, action: () -> Unit, content: @Composable () -> Unit) {
    val longClickScope = rememberCoroutineScope()
    val currentAction by rememberUpdatedState(action)

    SmallFloatingActionButton( onClick = {/*action()*/}, modifier = modifier
        .pointerInput(Unit){
            awaitEachGesture {
                    val event = awaitFirstDown(requireUnconsumed = false)

                    val longPresJob = longClickScope.launch {
                        currentAction()
                        delay(500)
                        while (true){
                            currentAction()
                            delay(300)
                        }
                    }
                    waitForUpOrCancellation()
                    longPresJob.cancel()
                }
            }

        , content = content,)

}

@Preview
@Composable
private fun XTrimPreview() {
    var xTrim by remember { mutableIntStateOf(0) }

    XTrim(modifier = Modifier.width(250.dp), trim = xTrim) { xTrim = it }
    
}