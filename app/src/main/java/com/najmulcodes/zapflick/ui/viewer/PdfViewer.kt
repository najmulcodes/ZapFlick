package com.najmulcodes.zapflick.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.viewer.PdfMath
import com.najmulcodes.zapflick.domain.viewer.PdfPageSize
import kotlin.math.roundToInt

private val PageGap = 8.dp

/**
 * One lazily rendered page per list item. Zoom works by making the pages wider: pages are drawn
 * at (viewport width x zoom) pixels and the content scrolls sideways inside the viewport. While a
 * pinch is in progress the content is only scaled visually, and the pages are re-rendered at the
 * final size when the fingers lift. The list state and the zoom are saved, so a rotation keeps
 * the position.
 */
@Composable
fun PdfViewer(
    pages: List<PdfPageSize>,
    cachedPage: (index: Int, widthPx: Int) -> Bitmap?,
    renderPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    var zoom by rememberSaveable { mutableFloatStateOf(PdfMath.MIN_ZOOM) }
    var gestureScale by remember { mutableFloatStateOf(1f) }
    var gestureOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    var gestureAnchorX by remember { mutableFloatStateOf(0f) }
    var pendingScroll by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    // After the pages get wider, move the sideways scroll so the zoom stays centred on the fingers.
    LaunchedEffect(zoom) {
        val target = pendingScroll ?: return@LaunchedEffect
        pendingScroll = null
        withFrameNanos { } // let the wider layout settle so the scroll range is current
        scrollState.scrollTo(target)
    }

    val commitZoom = rememberUpdatedState<(Float, Float) -> Unit> { newZoom, anchorX ->
        val clamped = PdfMath.clampZoom(newZoom)
        if (clamped != zoom) {
            pendingScroll = PdfMath.anchoredScroll(scrollState.value, anchorX, zoom, clamped)
            zoom = clamped
        }
    }

    val currentPage by remember(pages.size) {
        derivedStateOf {
            val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            PdfMath.currentPage(
                firstVisibleIndex = listState.firstVisibleItemIndex,
                firstVisibleOffsetPx = listState.firstVisibleItemScrollOffset,
                firstVisibleSizePx = firstItemSize,
                pageCount = pages.size,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // constraints are in pixels already, so this is screen width x density.
        val viewportWidthPx = constraints.maxWidth
        val pageWidthPx = (viewportWidthPx * zoom).roundToInt().coerceAtLeast(1)
        val pageWidth = with(LocalDensity.current) { pageWidthPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    onPinch = { zoomChange, centroid, container ->
                        if (gestureScale == 1f) {
                            gestureOrigin = TransformOrigin(
                                pivotFractionX = centroid.x / container.width.coerceAtLeast(1),
                                pivotFractionY = centroid.y / container.height.coerceAtLeast(1),
                            )
                            gestureAnchorX = centroid.x
                        }
                        gestureScale = (gestureScale * zoomChange)
                            .coerceIn(PdfMath.MIN_ZOOM / zoom, PdfMath.MAX_ZOOM / zoom)
                    },
                    onPinchEnd = {
                        commitZoom.value(zoom * gestureScale, gestureAnchorX)
                        gestureScale = 1f
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            commitZoom.value(PdfMath.nextDoubleTapZoom(zoom), tap.x)
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = gestureScale
                        scaleY = gestureScale
                        transformOrigin = gestureOrigin
                    },
            ) {
                Box(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(pageWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(PageGap),
                    ) {
                        items(count = pages.size, key = { it }) { index ->
                            PdfPageItem(
                                index = index,
                                size = pages[index],
                                widthPx = pageWidthPx,
                                cachedPage = cachedPage,
                                renderPage = renderPage,
                            )
                        }
                    }
                }
            }
        }

        PageIndicator(
            current = currentPage,
            total = pages.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    size: PdfPageSize,
    widthPx: Int,
    cachedPage: (Int, Int) -> Bitmap?,
    renderPage: suspend (Int, Int) -> Bitmap?,
) {
    val heightPx = PdfMath.pageSizeAtWidth(size, widthPx).height
    val height = with(LocalDensity.current) { heightPx.toDp() }

    // The previous bitmap stays on screen (stretched) while the sharper one is being drawn.
    var bitmap by remember(index) { mutableStateOf(cachedPage(index, widthPx)) }
    var failed by remember(index, widthPx) { mutableStateOf(false) }

    LaunchedEffect(index, widthPx) {
        failed = false
        val ready = cachedPage(index, widthPx) ?: renderPage(index, widthPx)
        if (ready != null) bitmap = ready else failed = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            val image = remember(shown) { shown.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.viewer_page_description, index + 1),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (failed) {
            Text(
                text = stringResource(R.string.viewer_page_failed),
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun PageIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = stringResource(R.string.viewer_page_indicator, current, total),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/**
 * Reports a two-finger pinch. One finger is never touched, so the list still scrolls. The gesture
 * is read before the children see it (Initial pass) so a pinch does not start a scroll.
 *
 * The callbacks are captured once (the pointer input never restarts), so they must only touch
 * Compose state, never plain local values.
 */
private fun Modifier.pinchToZoom(
    onPinch: (zoomChange: Float, centroid: Offset, container: IntSize) -> Unit,
    onPinchEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                pinching = true
                val zoomChange = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (zoomChange != 1f && centroid != Offset.Unspecified) {
                    onPinch(zoomChange, centroid, size)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) onPinchEnd()
    }
}
