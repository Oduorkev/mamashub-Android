package com.kabarak.kabarakmhis.pnc.babyTeethRecord

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.FhirEngine
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.fhir.FhirApplication
import com.kabarak.kabarakmhis.fhir.viewmodels.PatientDetailsViewModel
import com.kabarak.kabarakmhis.helperclass.FormatterClass
import com.kabarak.kabarakmhis.network_request.requests.RetrofitCallsFhir
import com.kabarak.kabarakmhis.pnc.data_class.BabyTeethRecordDataClass
import kotlinx.android.synthetic.main.activity_child_birth_view.btnAdd
import kotlinx.android.synthetic.main.activity_child_birth_view.tvANCID
import kotlinx.android.synthetic.main.activity_child_birth_view.tvAge
import kotlinx.android.synthetic.main.activity_child_birth_view.tvName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.hl7.fhir.r4.model.QuestionnaireResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BabyTeethViewRecord : AppCompatActivity() {

    private lateinit var BabyTeethRecyclerView: RecyclerView
    private lateinit var BabyTeethRecordAdapter: BabyTeethRecordAdapter
    private var BabychildTeeth: MutableList<BabyTeethRecordDataClass> = mutableListOf()
    private lateinit var retrofitCallsFhir: RetrofitCallsFhir
    private lateinit var noRecordView: View

    private lateinit var fhirEngine: FhirEngine
    private lateinit var formatter: FormatterClass
    private lateinit var patientId: String
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_baby_teeth_record_view)
        formatter = FormatterClass()
        fhirEngine = FhirApplication.fhirEngine(this)
        patientId = formatter.retrieveSharedPreference(this, "patientId").toString()

        patientDetailsViewModel = ViewModelProvider(
            this,
            PatientDetailsViewModel.PatientDetailsViewModelFactory(
                application,
                fhirEngine,
                patientId
            )
        )[PatientDetailsViewModel::class.java]
        btnAdd.setOnClickListener {
            val intent = Intent(this, BabyTeethDevelopmentRecord::class.java)
            startActivity(intent)
        }
        BabyTeethRecyclerView = findViewById(R.id.recycler_view_baby_teeth)
        BabyTeethRecyclerView.layoutManager = LinearLayoutManager(this)
        BabyTeethRecordAdapter = BabyTeethRecordAdapter(BabychildTeeth) { rawResponseId ->
            val responseId = extractResponseId(rawResponseId)
            Toast.makeText(this, "Response ID: $responseId", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, BabyTeethEdit::class.java)
            intent.putExtra("responseId", responseId)
            startActivity(intent)
        }
        BabyTeethRecyclerView.adapter = BabyTeethRecordAdapter
        noRecordView = findViewById(R.id.no_record)

        retrofitCallsFhir = RetrofitCallsFhir()
        fetchBabyTeethFromFHIR()
        fetchPatientData()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun fetchPatientData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val patientLocalName = formatter.retrieveSharedPreference(this@BabyTeethViewRecord, "patientName")
                val patientLocalDob = formatter.retrieveSharedPreference(this@BabyTeethViewRecord, "dob")
                val patientLocalIdentifier = formatter.retrieveSharedPreference(this@BabyTeethViewRecord, "identifier")

                if (patientLocalName.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val progressDialog = ProgressDialog(this@BabyTeethViewRecord).apply {
                            setTitle("Please wait...")
                            setMessage("Fetching patient details...")
                            show()
                        }
                        var patientName = ""
                        var dob = ""
                        val job = Job()
                        CoroutineScope(Dispatchers.IO + job).launch {
                            val patientData = getPatientDataFromFhirEngine()
                            patientName = patientData.first
                            dob = patientData.second
                            formatter.saveSharedPreference(this@BabyTeethViewRecord, "patientName", patientName)
                            formatter.saveSharedPreference(this@BabyTeethViewRecord, "dob", dob)
                        }.join()
                        showPatientDetails(patientName, dob, patientLocalIdentifier)
                        progressDialog.dismiss()
                    }
                } else {
                    showPatientDetails(patientLocalName, patientLocalDob, patientLocalIdentifier)
                }
            } catch (e: Exception) {
                Log.e("BabyTeethView", "Error fetching patient data: ${e.message}")
            }
        }
    }

    private fun showPatientDetails(patientName: String, dob: String?, identifier: String?) {
        tvName.text = patientName
        if (!identifier.isNullOrEmpty()) tvANCID.text = identifier
        if (!dob.isNullOrEmpty()) tvAge.text = "${formatter.calculateAge(dob)} years"
    }

    private fun getPatientDataFromFhirEngine(): Pair<String, String> {
        val patientData = patientDetailsViewModel.getPatientData()
        return Pair(patientData.name, patientData.dob)
    }

    private fun fetchBabyTeethFromFHIR() {
        lifecycleScope.launch(Dispatchers.IO) {
            retrofitCallsFhir.fetchAllQuestionnaireResponses(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        response.body()?.let { responseBody ->
                            val rawResponse = responseBody.string()
                            Log.d("BabyTeethViewRecord", "Raw Response body: $rawResponse")
                            if (rawResponse.isNotEmpty()) {
                                try {
                                    val fhirContext = FhirContext.forR4()
                                    val parser = fhirContext.newJsonParser()
                                    val bundle = parser.parseResource(org.hl7.fhir.r4.model.Bundle::class.java, rawResponse)
                                    BabychildTeeth.clear()
                                    extractChildrenFromBundle(bundle)
                                    runOnUiThread { toggleViews() }
                                } catch (e: Exception) {
                                    Log.e("ChildViewActivity", "Error parsing response", e)
                                    runOnUiThread {
                                        Toast.makeText(this@BabyTeethViewRecord, "Failed to parse response", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(this@BabyTeethViewRecord, "Received an empty response", Toast.LENGTH_SHORT).show()
                                    toggleViews()
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@BabyTeethViewRecord, "Failed to fetch data: ${response.message()}", Toast.LENGTH_SHORT).show()
                            toggleViews()
                        }
                    }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    runOnUiThread {
                        Log.e("ChildViewActivity", "Error occurred while fetching data", t)
                        Toast.makeText(this@BabyTeethViewRecord, "Error occurred: ${t.message}", Toast.LENGTH_SHORT).show()
                        toggleViews()
                    }
                }
            })
        }
    }
private fun extractChildrenFromBundle(bundle: org.hl7.fhir.r4.model.Bundle) {
    for (entry in bundle.entry) {
        val resource = entry.resource
        if (resource is QuestionnaireResponse) {
            // Extract child from each QuestionnaireResponse
            extractChildrenFromQuestionnaire(resource)
        }
    }

    // Notify the adapter to update the UI with the new children data
    runOnUiThread {
        BabyTeethRecordAdapter.notifyDataSetChanged()
    }
}
//    private fun extractChildrenFromBundle(bundle: org.hl7.fhir.r4.model.Bundle) {
//        for (entry in bundle.entry) {
//            val resource = entry.resource
//            if (resource is QuestionnaireResponse) {
//                extractChildrenFromQuestionnaire(resource)
//            }
//        }
//        runOnUiThread { BabyTeethRecordAdapter.notifyDataSetChanged() }
//    }
    private fun extractChildrenFromQuestionnaire(questionnaireResponse: QuestionnaireResponse) {
        val responseId = questionnaireResponse.id
        if (BabychildTeeth.any { it.id == responseId }){
            Log.d("BabyTeethView ", "Teeth Record with ID $responseId already exists. Skipping duplicate.")
            return
        }
    Log.d("Linus Teeth LInk Id", "Item linkId: ${questionnaireResponse.item}")
        for (item in questionnaireResponse.item) {
            Log.d("Baby Teeth LInk Id", "Item linkId: ${item.linkId}")
            if (item.linkId =="bce1a121-541a-469c-a48b-5ffdfff9348a") {
                for (answerItem in item.answer) {
                    val teethType = answerItem.valueCoding?.code
                    var ageSeen: String? = null
                    var dateSeen: String? = null

                    for (subItem in answerItem.item) {
                        //Log.d("BabyTeethViewRecord", "SubItem linkId: ${subItem.linkId}")

                        when (subItem.linkId) {
                            "f1ed5d7d-620c-4616-d65a-116d230eaed2" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "29c06a8c-2859-4534-81d8-f37da7d204bd" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "6c40bb81-0467-4545-8c17-5c434a96da02" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "d6762c31-9e5b-4920-b819-192093dd51c4" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "ebc7dacd-45d8-4b70-c14d-037d096672e8" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "cab9310c-d2f7-423e-85f6-888589013376" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "b7574308-f4b4-4f8b-8f43-8b8b22536fa4" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "bbf33c2f-9ca3-4c96-8be2-fd342730df00" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "2df30fe4-d530-48e3-813c-a50e599674a5" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "dba37247-df87-47d6-8682-4d82938d37c0" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "6610fa4e-accc-4e89-e2c5-9f4431498dbd" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "39a89d85-993c-45cb-8e9a-de417ca00ed8" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "9efd1210-e8f6-44cf-89b5-5ddd9e967605" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "14dadb0a-9fc4-4479-88dd-cb87d14353b1" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            "900f8e31-f8c8-47a8-8821-f6738099f293" -> ageSeen = subItem.answer.firstOrNull()?.valueIntegerType?.value?.toString()
                            "31897de0-7f30-4c2a-88e8-d04a815cef3d" -> dateSeen = subItem.answer.firstOrNull()?.valueDateType?.value?.toString()
                            else -> Log.d("BabyTeethViewRecord", "Unknown linkId: ${subItem.linkId}")
                        }

                        // Log for each sub-item
                        Log.d("BabyTeethViewRecord", "Teeth Type: $teethType, Age Seen: $ageSeen, Date Seen: $dateSeen")
                    }
                    // Add an entry only if both fields are available
                    if (!ageSeen.isNullOrEmpty() && !dateSeen.isNullOrEmpty()) {
                        BabychildTeeth.add(BabyTeethRecordDataClass(responseId, ageSeen, dateSeen, teethType))
                        Log.d("BabyTeethViewRecord", "Added record -> Teeth Type: $teethType, Age Seen: $ageSeen, Date Seen: $dateSeen")
                    }
                }
            }
        }
    }


    private fun extractResponseId(rawResponseId: String): String {
        val regex = Regex("QuestionnaireResponse/(\\d+)")
        val matchResult = regex.find(rawResponseId)
        return matchResult?.groupValues?.get(1) ?: rawResponseId
    }

    private fun toggleViews() {
        BabyTeethRecyclerView.visibility = if (BabychildTeeth.isEmpty()) View.GONE else View.VISIBLE
        noRecordView.visibility = if (BabychildTeeth.isEmpty()) View.VISIBLE else View.GONE
    }

}
