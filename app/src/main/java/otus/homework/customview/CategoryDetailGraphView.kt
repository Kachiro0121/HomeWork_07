package otus.homework.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import kotlin.math.roundToInt

class CategoryDetailGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {


    companion object{
        private const val KEY_DATA_CATEGORY = "data"
        private const val KEY_CATEGORY = "category"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_DATE = "date"
        private const val KEY_COLOR = "color"

        private const val FILE = "payload.json"
    }

    private var data: List<CategoryGraph> = listOf()
    private val padding = 170f

    data class CategoryGraph(
        var category: String = "",
        var data: List<DataPoint> = listOf(),
        var color: Int = Color.BLACK
    )

    data class DataPoint(
        var date: String = "",
        var amount: Float = 0f
    )

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 32f
    }

    private val gridSteps = 10

    init {
        if (isInEditMode) loadData()
    }

    private fun loadData() {
        val categories = parseJsonToCategories(FILE)
        setData(categories)
    }

    fun setData(newData: List<CategoryGraph>) {
        data = newData
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 800
        val desiredHeight = 400

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) return

        val graphWidth = width - 1.5f * padding
        val graphHeight = height - 2 * padding

        val maxAmount = data.flatMap { it.data }.maxOf { it.amount } * 1.1f
        val stepX = graphWidth / (data.firstOrNull()?.data?.size?.minus(1) ?: 1)
        val scaleY = graphHeight / maxAmount

        drawGrid(canvas, graphHeight, maxAmount, stepX)
        drawLines(canvas, scaleY, stepX)
    }

    private fun drawGrid(canvas: Canvas, graphHeight: Float, maxAmount: Float, stepX: Float) {
        val stepY = graphHeight / gridSteps

        for (i in 0..gridSteps) {
            val y = height - padding - i * stepY
            canvas.drawLine(padding, y, width - padding / 2, y, paintGrid)
            val value = (maxAmount / gridSteps * i).roundToInt()
            canvas.drawText("$value ₽", padding - 150, y + 10, paintText)
        }

        val dates = data.firstOrNull()?.data?.map { it.date } ?: listOf()
        for (i in dates.indices) {
            val x = padding + i * stepX
            canvas.drawLine(x, height - padding, x, padding, paintGrid)
            if (dates.isNotEmpty()) {
                canvas.drawText(dates[i], x - 40, height - padding + 40, paintText)
            }
        }
    }

    private fun drawLines(canvas: Canvas, scaleY: Float, stepX: Float) {
        data.forEach { category ->
            val path = Path()
            paintLine.color = category.color

            val points = category.data.mapIndexed { index, dataPoint ->
                val x = padding + index * stepX
                val y = height - padding - dataPoint.amount * scaleY
                x to y
            }

            if (points.size > 1) {
                path.moveTo(points[0].first, points[0].second)

                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val current = points[i]

                    val controlPointX1 = (prev.first + current.first) / 2
                    val controlPointY1 = prev.second
                    val controlPointX2 = (prev.first + current.first) / 2
                    val controlPointY2 = current.second

                    path.cubicTo(
                        controlPointX1, controlPointY1,
                        controlPointX2, controlPointY2,
                        current.first, current.second
                    )
                }
            }

            canvas.drawPath(path, paintLine)
        }
    }

    fun loadDataFromJson(jsonFile: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val categories = parseJsonToCategories(jsonFile)
                withContext(Dispatchers.Main) {
                    setData(categories)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseJsonToCategories(jsonFile: String): List<CategoryGraph> {
        val json = context.assets.open(jsonFile).bufferedReader().use(BufferedReader::readText)
        val jsonArray = JSONArray(json)

        return (0 until jsonArray.length()).map { index ->
            val categoryObject = jsonArray.getJSONObject(index)
            val categoryName = categoryObject.getString(KEY_CATEGORY)
            val color = Color.parseColor(categoryObject.getString(KEY_COLOR))
            val dataArray = categoryObject.getJSONArray(KEY_DATA_CATEGORY)

            val dataPoints = (0 until dataArray.length()).map { dataIndex ->
                val dataObject = dataArray.getJSONObject(dataIndex)
                DataPoint(
                    date = dataObject.getString(KEY_DATE),
                    amount = dataObject.getDouble(KEY_AMOUNT).toFloat()
                )
            }

            CategoryGraph(
                category = categoryName,
                data = dataPoints,
                color = color
            )
        }
    }

}
