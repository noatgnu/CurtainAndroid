package info.proteo.curtain.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import info.proteo.curtain.domain.model.AnnotationEditCandidate
import info.proteo.curtain.domain.model.CurtainData
import kotlin.math.sqrt

@Composable
fun AnnotationEditOverlay(
    curtainData: CurtainData,
    isInteractivePositioning: Boolean,
    positioningCandidate: AnnotationEditCandidate?,
    isShowingDragPreview: Boolean,
    dragStartPosition: Offset?,
    currentDragPosition: Offset?,
    onAnnotationTapped: (Offset) -> Unit,
    onAnnotationDragged: (Offset) -> Unit = {},
    onDragEnded: () -> Unit = {},
    viewSize: androidx.compose.ui.geometry.Size? = null,
    modifier: Modifier = Modifier
) {
    val toolbarHeight = with(LocalDensity.current) { 56.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isInteractivePositioning) {
                detectTapGestures { offset ->
                    if (offset.y > toolbarHeight) {
                        onAnnotationTapped(offset)
                    }
                }
            }
            .pointerInput(isInteractivePositioning) {
                if (isInteractivePositioning) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            if (change.position.y > toolbarHeight) {
                                onAnnotationDragged(change.position)
                            }
                        },
                        onDragEnd = {
                            onDragEnded()
                        }
                    )
                }
            }
    ) {
        if (isShowingDragPreview && dragStartPosition != null && currentDragPosition != null) {
            DragPreviewOverlay(
                startPosition = dragStartPosition,
                currentPosition = currentDragPosition
            )
        }
    }
}

@Composable
private fun DragPreviewOverlay(
    startPosition: Offset,
    currentPosition: Offset
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val orangeColor = Color(0xFFFF9800)
    val greenColor = Color(0xFF4CAF50)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)

        drawLine(
            color = primaryColor,
            start = startPosition,
            end = currentPosition,
            strokeWidth = 6f,
            pathEffect = pathEffect
        )

        drawCircle(
            color = orangeColor,
            center = startPosition,
            radius = 14f,
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = orangeColor,
            center = startPosition,
            radius = 7f
        )

        drawCircle(
            color = greenColor,
            center = currentPosition,
            radius = 16f,
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = greenColor,
            center = currentPosition,
            radius = 8f
        )
    }

    val distance = sqrt(
        (currentPosition.x - startPosition.x) * (currentPosition.x - startPosition.x) +
        (currentPosition.y - startPosition.y) * (currentPosition.y - startPosition.y)
    )
    val midPoint = Offset(
        (startPosition.x + currentPosition.x) / 2,
        (startPosition.y + currentPosition.y) / 2 - 40
    )

    Box(
        modifier = Modifier
            .offset(
                x = with(LocalDensity.current) { midPoint.x.toDp() - 30.dp },
                y = with(LocalDensity.current) { midPoint.y.toDp() }
            )
    ) {
        androidx.compose.material3.Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "${distance.toInt()}px",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(
                x = with(LocalDensity.current) { startPosition.x.toDp() - 50.dp },
                y = with(LocalDensity.current) { (startPosition.y - 50).toDp() }
            )
    ) {
        androidx.compose.material3.Surface(
            color = orangeColor.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                "START: (${startPosition.x.toInt()}, ${startPosition.y.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(
                x = with(LocalDensity.current) { currentPosition.x.toDp() - 50.dp },
                y = with(LocalDensity.current) { (currentPosition.y - 50).toDp() }
            )
    ) {
        androidx.compose.material3.Surface(
            color = greenColor.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                "END: (${currentPosition.x.toInt()}, ${currentPosition.y.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun DataPointHighlightOverlay(
    curtainData: CurtainData,
    positioningCandidate: AnnotationEditCandidate?,
    viewSize: androidx.compose.ui.geometry.Size
) {
    positioningCandidate?.let { candidate ->
        val volcanoAxis = curtainData.settings.volcanoAxis
        val marginLeft = 70.0
        val marginRight = 40.0
        val marginTop = 60.0
        val marginBottom = 120.0

        val plotAreaWidth = viewSize.width - marginLeft - marginRight
        val plotAreaHeight = viewSize.height - marginTop - marginBottom

        val xMin = volcanoAxis.minX ?: -3.0
        val xMax = volcanoAxis.maxX ?: 3.0
        val yMin = volcanoAxis.minY ?: 0.0
        val yMax = volcanoAxis.maxY ?: 6.0

        val x = candidate.arrowPosition.first
        val y = candidate.arrowPosition.second

        val viewX = marginLeft + ((x - xMin) / (xMax - xMin)) * plotAreaWidth
        val viewY = viewSize.height - marginBottom - ((y - yMin) / (yMax - yMin)) * plotAreaHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Blue.copy(alpha = 0.5f),
                radius = 10f,
                center = Offset(viewX.toFloat(), viewY.toFloat()),
                style = Stroke(width = 3f)
            )
        }

        Box(
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { viewX.toFloat().toDp() - 10.dp },
                    y = with(LocalDensity.current) { viewY.toFloat().toDp() - 10.dp }
                )
        ) {
            Text("📍", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PreviewModeIndicator() {
    androidx.compose.material3.Surface(
        color = Color(0xFF4CAF50).copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            "📝➡️📍 Preview: Drag to move text, then Accept or Cancel",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun AnnotationEditModeIndicator() {
    androidx.compose.material3.Surface(
        color = Color(0xFFFF9800).copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            "🎯 Annotation Edit Mode - Tap near annotations to edit",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
