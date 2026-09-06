package com.comicify.feature.reader.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.comicify.feature.reader.domain.PageLook

private val LookPaints: Map<PageLook, Paint> = PageLook.entries
    .mapNotNull { look ->
        look.filter()?.let { matrix -> look to Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) } }
    }
    .toMap()

fun Modifier.pageLook(look: PageLook): Modifier {
    val paint = LookPaints[look] ?: return this
    return drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}
