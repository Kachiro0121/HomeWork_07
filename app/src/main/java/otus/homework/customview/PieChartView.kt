package otus.homework.customview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object{
        private const val KEY_SUPER_STATE = "KEY_SUPER_STATE"
        private const val KEY_DATA = "KEY_DATA"

        private const val KEY_DATA_CATEGORY = "data"
        private const val KEY_CATEGORY = "category"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_COLOR = "color"

        private const val TEXT_CENTER = "Tap a sector"

        private const val FILE = "payload.json"
    }

    private val rectF = RectF()
    private var selectedCategoryIndex: Int? = null
    private var animationProgress: Float = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f.dp
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 64f
        textAlign = Paint.Align.CENTER
    }

    private var data: List<Category> = emptyList()
    private var onSectorClickListener: ((Category) -> Unit)? = null
    private var angles = mutableListOf<AngleRange>()

    private var centerText: String = TEXT_CENTER

    init {
        if (isInEditMode) setData(getCategories(FILE))
    }

    fun setData(newData: List<Category>) {
        data = newData
        invalidate()
    }

    fun setCenterText(text: String){
        centerText = text
    }

    fun setCenterText(@StringRes res: Int) {
        setCenterText(context.getString(res))
    }

    fun setOnSectorClickListener(listener: (Category) -> Unit) {
        onSectorClickListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 300
        val width = resolveSize(desiredSize, widthMeasureSpec)
        val height = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) return

        val total = data.sumOf{ it.amount.toDouble() }.toFloat()
        val radius = (width / 3f).coerceAtMost(height / 3f)
        val cx = width / 2f
        val cy = height / 2f

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)
        var startAngle = 0f

        updateAngles(data, total)

        data.forEachIndexed { index, category ->
            paint.color = category.color
            val sweepAngle = (category.amount / total) * 360f

            if (index == selectedCategoryIndex) {
                val angleMiddle = startAngle + sweepAngle / 2
                val offsetX = (10f.dp * animationProgress) * cos(Math.toRadians(angleMiddle.toDouble())).toFloat()
                val offsetY = (10f.dp * animationProgress) * sin(Math.toRadians(angleMiddle.toDouble())).toFloat()
                rectF.offset(offsetX, offsetY)

                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)

                rectF.offset(-offsetX, -offsetY)
            } else {
                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
            }
            startAngle += sweepAngle
        }

        canvas.drawText(centerText, cx, cy + textPaint.textSize / 3, textPaint)
    }

    private fun updateAngles(data: List<Category>, total: Float) {
        var startAngle = 0f
        angles.clear()

        data.forEach { category ->
            val sweepAngle = (category.amount / total) * 360f
            angles.add(AngleRange(startAngle, startAngle + sweepAngle))
            startAngle += sweepAngle
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action){
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                val cx = width / 2f
                val cy = height / 2f
                val dx = event.x - cx
                val dy = event.y - cy
                val distanceFromCenter = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                val radius = (width / 3f).coerceAtMost(height / 3f)
                val strokeWidth = paint.strokeWidth

                if (distanceFromCenter in (radius - strokeWidth / 2)..(radius + strokeWidth / 2)) {
                    val touchAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                    angles.forEachIndexed { index, range ->
                        if (touchAngle in range.startAngle..range.endAngle) {
                            onSectorClickListener?.invoke(data[index])
                            animateSelection(index)
                            return true
                        }
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putParcelableArrayList(KEY_DATA, ArrayList(data))
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val savedData = state.getParcelableArrayList<Category>(KEY_DATA)
            if (savedData != null) {
                data = savedData
                updateAngles(data, data.sumOf { it.amount.toDouble() }.toFloat())
            }
            super.onRestoreInstanceState(state.getParcelable(KEY_SUPER_STATE))
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private fun getCategories(jsonFile: String): List<Category> {
        val json = context.assets.open(jsonFile).bufferedReader().use(BufferedReader::readText)
        val jsonArray = JSONArray(json)
        return (0 until jsonArray.length()).map { index ->
            val item = jsonArray.getJSONObject(index)
            Category(
                name = item.getString(KEY_CATEGORY),
                amount = getSum(item.getJSONArray(KEY_DATA_CATEGORY)),
                color = Color.parseColor(item.getString(KEY_COLOR))
            )
        }
    }

    private fun getSum(amounts: JSONArray): Float {
        var totalAmount = 0f
        for (i in 0 until amounts.length()) {
            val item = amounts.getJSONObject(i)
            totalAmount += item.getDouble(KEY_AMOUNT).toFloat()
        }
        return totalAmount
    }

    fun loadDataFromJson(jsonFile: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val categories = getCategories(jsonFile)
                withContext(Dispatchers.Main) {
                    setData(categories)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun animateSelection(index: Int) {
        selectedCategoryIndex = index
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animator.start()
    }

    data class Category(
        val name: String,
        val amount: Float,
        val color: Int
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString() ?: "",
            parcel.readFloat(),
            parcel.readInt()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(name)
            parcel.writeFloat(amount)
            parcel.writeInt(color)
        }

        override fun describeContents(): Int = 0

        private companion object CREATOR : Parcelable.Creator<Category> {
            override fun createFromParcel(parcel: Parcel): Category {
                return Category(parcel)
            }

            override fun newArray(size: Int): Array<Category?> {
                return arrayOfNulls(size)
            }
        }
    }

    private data class AngleRange(val startAngle: Float, val endAngle: Float)


}
