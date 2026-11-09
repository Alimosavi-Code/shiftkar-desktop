package com.seyedhosseinmousavisaeedi.shiftkar.util

import kotlin.concurrent.Volatile

import androidx.compose.ui.graphics.ImageBitmap
import com.seyedhosseinmousavisaeedi.shiftkar.platform.decodePngToImageBitmap
import com.seyedhosseinmousavisaeedi.shiftkar.platform.loadBundledFontBytes
import java.awt.BasicStroke
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import javax.imageio.ImageIO

/**
 * Multiplatform JVM/AWT implementation of Android's 2D Canvas API (`Bitmap`, `Canvas`,
 * `Paint`, `RectF`, `LinearGradient`, `RadialGradient`, `Typeface`, `Shader`) so that
 * the high-resolution Frost `ImageExporter` renders identical PNG cards on Desktop!
 */

object Color {
    const val WHITE: Int = -0x1
    const val BLACK: Int = -0x1000000
    const val TRANSPARENT: Int = 0

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)

    fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)
}

class Typeface(val awtFont: Font) {
    companion object {
        private fun loadBundledAwtFont(fileName: String, fallbackStyle: Int): Font = runCatching {
            val bytes = loadBundledFontBytes(fileName)
            Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(bytes))
        }.getOrElse {
            Font("Tahoma", fallbackStyle, 16)
        }

        val REGULAR: Typeface by lazy { Typeface(loadBundledAwtFont("iransansx_regular.ttf", Font.PLAIN)) }
        val BOLD: Typeface by lazy { Typeface(loadBundledAwtFont("iransansx_bold.ttf", Font.BOLD)) }
        val EXTRABOLD: Typeface by lazy { Typeface(loadBundledAwtFont("iransansx_extrabold.ttf", Font.BOLD)) }
        val BLACK: Typeface by lazy { Typeface(loadBundledAwtFont("iransansx_black.ttf", Font.BOLD)) }

        val DEFAULT: Typeface get() = REGULAR
        val DEFAULT_BOLD: Typeface get() = BOLD
    }
}

open class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
}

class LinearGradient(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val color0: Int,
    val color1: Int,
    val tile: TileMode = TileMode.CLAMP
) : Shader()

class RadialGradient(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val colors: IntArray,
    val stops: FloatArray?,
    val tile: TileMode = TileMode.CLAMP
) : Shader()

class RectF(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) * 0.5f
    fun centerY(): Float = (top + bottom) * 0.5f
}

class Paint(val flags: Int = 0) {
    companion object {
        const val ANTI_ALIAS_FLAG: Int = 1
        const val SUBPIXEL_TEXT_FLAG: Int = 2

        private val dummyImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    }

    enum class Style { FILL, STROKE }
    enum class Align { LEFT, CENTER, RIGHT }

    class FontMetrics(val ascent: Float, val descent: Float)

    var style: Style = Style.FILL
    var color: Int = Color.BLACK
    var strokeWidth: Float = 1f
    var textSize: Float = 16f
    var textAlign: Align = Align.LEFT
    var typeface: Typeface = Typeface.DEFAULT
    var shader: Shader? = null

    fun derivedAwtFont(): Font = typeface.awtFont.deriveFont(textSize)

    val fontMetrics: FontMetrics
        get() {
            val g = dummyImg.createGraphics()
            val fm = g.getFontMetrics(derivedAwtFont())
            g.dispose()
            return FontMetrics(ascent = -fm.ascent.toFloat(), descent = fm.descent.toFloat())
        }

    fun measureText(text: String): Float {
        if (text.isEmpty()) return 0f
        val g = dummyImg.createGraphics()
        val fm = g.getFontMetrics(derivedAwtFont())
        val w = fm.stringWidth(text).toFloat()
        g.dispose()
        return w
    }
}

class Bitmap(val width: Int, val height: Int) {
    enum class Config { ARGB_8888 }

    val awtImage: BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    @Volatile
    private var cachedPngBytes: ByteArray? = null

    @Volatile
    private var cachedComposeBitmap: ImageBitmap? = null

    fun toPngBytes(): ByteArray {
        cachedPngBytes?.let { return it }
        val baos = ByteArrayOutputStream(65536)
        ImageIO.write(awtImage, "PNG", baos)
        val bytes = baos.toByteArray()
        cachedPngBytes = bytes
        return bytes
    }

    fun asImageBitmap(): ImageBitmap {
        cachedComposeBitmap?.let { return it }
        val img = decodePngToImageBitmap(toPngBytes())
        cachedComposeBitmap = img
        return img
    }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config = Config.ARGB_8888): Bitmap =
            Bitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
}

class Canvas(private val bitmap: Bitmap) {
    private val g2: Graphics2D = bitmap.awtImage.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    private val transformStack = ArrayDeque<AffineTransform>()

    private fun intToAwtColor(argb: Int): java.awt.Color = java.awt.Color(argb, true)

    private fun applyPaint(paint: Paint) {
        val sh = paint.shader
        when (sh) {
            is LinearGradient -> {
                val p0 = Point2D.Float(sh.x0, sh.y0)
                val p1 = Point2D.Float(sh.x1, sh.y1)
                if (p0.distance(p1) < 0.01) {
                    g2.paint = intToAwtColor(sh.color0)
                } else {
                    g2.paint = GradientPaint(p0, intToAwtColor(sh.color0), p1, intToAwtColor(sh.color1))
                }
            }
            is RadialGradient -> {
                val r = sh.radius.coerceAtLeast(1f)
                val stops = sh.stops ?: FloatArray(sh.colors.size) { idx ->
                    idx.toFloat() / (sh.colors.size - 1).coerceAtLeast(1)
                }
                val awtColors = Array(sh.colors.size) { idx -> intToAwtColor(sh.colors[idx]) }
                g2.paint = RadialGradientPaint(
                    Point2D.Float(sh.centerX, sh.centerY),
                    r,
                    stops,
                    awtColors,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE
                )
            }
            else -> {
                g2.paint = intToAwtColor(paint.color)
            }
        }
        g2.stroke = BasicStroke(
            paint.strokeWidth.coerceAtLeast(0.5f),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        )
    }

    fun save() {
        transformStack.push(g2.transform)
    }

    fun restore() {
        if (transformStack.isNotEmpty()) {
            g2.transform = transformStack.pop()
        }
    }

    fun rotate(degrees: Float, px: Float, py: Float) {
        g2.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())
    }

    fun drawColor(color: Int) {
        g2.paint = intToAwtColor(color)
        g2.fillRect(0, 0, bitmap.width, bitmap.height)
    }

    fun drawRect(rect: RectF, paint: Paint) {
        drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val w = (right - left).coerceAtLeast(0f)
        val h = (bottom - top).coerceAtLeast(0f)
        if (w <= 0f || h <= 0f) return
        applyPaint(paint)
        val shape = Rectangle2D.Float(left, top, w, h)
        if (paint.style == Paint.Style.STROKE) {
            g2.draw(shape)
        } else {
            g2.fill(shape)
        }
    }

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
        val w = rect.width().coerceAtLeast(0f)
        val h = rect.height().coerceAtLeast(0f)
        if (w <= 0f || h <= 0f) return
        applyPaint(paint)
        val shape = RoundRectangle2D.Float(rect.left, rect.top, w, h, rx * 2f, ry * 2f)
        if (paint.style == Paint.Style.STROKE) {
            g2.draw(shape)
        } else {
            g2.fill(shape)
        }
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        if (radius <= 0f) return
        applyPaint(paint)
        val shape = Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f)
        if (paint.style == Paint.Style.STROKE) {
            g2.draw(shape)
        } else {
            g2.fill(shape)
        }
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        applyPaint(paint)
        g2.draw(Line2D.Float(x1, y1, x2, y2))
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        applyPaint(paint)
        val font = paint.derivedAwtFont()
        g2.font = font
        val fm = g2.getFontMetrics(font)
        val textW = fm.stringWidth(text).toFloat()
        val drawX = when (paint.textAlign) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - textW * 0.5f
            Paint.Align.RIGHT -> x - textW
        }
        g2.drawString(text, drawX, y)
    }
}
