package otus.homework.customview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    companion object{
        private const val FILE_NAME = "payload.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val pieChartView = findViewById<PieChartView>(R.id.pie_chart_view)
        val diagram = findViewById<CategoryDetailGraphView>(R.id.detail_graph)

        pieChartView.loadDataFromJson(FILE_NAME)
        diagram.loadDataFromJson(FILE_NAME)

        pieChartView.setOnSectorClickListener  { category ->
            pieChartView.setCenterText("${category.name}: ${category.amount}")
        }

    }



}