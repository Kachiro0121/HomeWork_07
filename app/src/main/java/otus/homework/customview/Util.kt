package otus.homework.customview

import android.content.res.Resources
import android.util.TypedValue

val Number.dp get() =  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics)