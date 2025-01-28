package com.kabarak.kabarakmhis.pnc.inactivated_polio_vaccine

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.fhir.FhirApplication
import com.kabarak.kabarakmhis.helperclass.FormatterClass
import com.kabarak.kabarakmhis.pnc.data_class.IPV
import com.kabarak.kabarakmhis.fhir.viewmodels.PatientDetailsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.FhirEngine
import com.kabarak.kabarakmhis.network_request.requests.RetrofitCallsFhir
import kotlinx.android.synthetic.main.activity_inactivated_polio_vaccine_view.btnAdd
import kotlinx.android.synthetic.main.activity_inactivated_polio_vaccine_view.tvANCID
import kotlinx.android.synthetic.main.activity_inactivated_polio_vaccine_view.tvAge
import kotlinx.android.synthetic.main.activity_inactivated_polio_vaccine_view.tvName
import org.hl7.fhir.r4.model.DateType
import org.hl7.fhir.r4.model.QuestionnaireResponse

class InactivatedPolioVaccineView : AppCompatActivity() {

    private lateinit var childRecyclerView: RecyclerView
    private lateinit var inactivatedPolioVaccineAdapter: InactivatedPolioVaccineAdapter
    private var ipvs: MutableList<IPV> = mutableListOf()
    private lateinit var retrofitCallsFhir: RetrofitCallsFhir
    private lateinit var noRecordView: View  // View for the no_record layout

    // For fetching patient data
    private lateinit var fhirEngine: FhirEngine
    private lateinit var formatter: FormatterClass
    private lateinit var patientId: String
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inactivated_polio_vaccine_view)

        // Initialize FHIR engine and formatter
        formatter = FormatterClass()
        fhirEngine = FhirApplication.fhirEngine(this)

        // Retrieve patient ID from shared preferences
        patientId = formatter.retrieveSharedPreference(this, "patientId").toString()

        // Initialize ViewModel
        patientDetailsViewModel = ViewModelProvider(
            this,
            PatientDetailsViewModel.PatientDetailsViewModelFactory(application, fhirEngine, patientId)
        )[PatientDetailsViewModel::class.java]

        btnAdd.setOnClickListener {
            val intent = Intent(this, InactivatedPolioVaccine::class.java)
            startActivity(intent)
        }

        // Initialize RecyclerView
        childRecyclerView = findViewById(R.id.recycler_view_child)
        childRecyclerView.layoutManager = LinearLayoutManager(this)

        inactivatedPolioVaccineAdapter = InactivatedPolioVaccineAdapter(ipvs) { rawResponseId ->
            val responseId = extractResponseId(rawResponseId)
            Toast.makeText(this, "Response ID: $responseId", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, InactivatedPolioVaccineEdit::class.java)
            intent.putExtra("responseId", responseId)
            startActivity(intent)
        }
        childRecyclerView.adapter = inactivatedPolioVaccineAdapter

        // Initialize noRecordView (the include layout for "no records found")
        noRecordView = findViewById(R.id.no_record)

        // Initialize RetrofitCallsFhir
        retrofitCallsFhir = RetrofitCallsFhir()

        // Fetch child data from FHIR server
        fetchChildrenFromFHIR()

        // Fetch patient data
        fetchPatientData()
    }

    private fun fetchPatientData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val patientLocalName = formatter.retrieveSharedPreference(this@InactivatedPolioVaccineView, "patientName")
                val patientLocalDob = formatter.retrieveSharedPreference(this@InactivatedPolioVaccineView, "dob")
                val patientLocalIdentifier = formatter.retrieveSharedPreference(this@InactivatedPolioVaccineView, "identifier")

                if (patientLocalName.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val progressDialog = ProgressDialog(this@InactivatedPolioVaccineView)
                        progressDialog.setTitle("Please wait...")
                        progressDialog.setMessage("Fetching patient details...")
                        progressDialog.show()

                        var patientName: String = ""
                        var dob: String = ""
                        var identifier: String = ""

                        val job = Job()
                        CoroutineScope(Dispatchers.IO + job).launch {
                            val patientData = getPatientDataFromFhirEngine()
                            patientName = patientData.first
                            dob = patientData.second

                            formatter.saveSharedPreference(this@InactivatedPolioVaccineView, "patientName", patientName)
                            formatter.saveSharedPreference(this@InactivatedPolioVaccineView, "dob", dob)

                            if (identifier.isNotEmpty()) {
                                formatter.saveSharedPreference(this@InactivatedPolioVaccineView, "identifier", identifier)
                            }
                        }.join()

                        showPatientDetails(patientName, dob, identifier)

                        progressDialog.dismiss()
                    }
                } else {
                    // Display the data from local storage
                    showPatientDetails(patientLocalName, patientLocalDob, patientLocalIdentifier)
                }
            } catch (e: Exception) {
                Log.e("InactivatedPolioVaccineView", "Error fetching patient data: ${e.message}")
            }
        }
    }

    private fun showPatientDetails(patientName: String, dob: String?, identifier: String?) {
        tvName.text = patientName
        if (!identifier.isNullOrEmpty()) tvANCID.text = identifier
        if (!dob.isNullOrEmpty()) tvAge.text = "${formatter.calculateAge(dob)} years"
    }

    private fun getPatientDataFromFhirEngine(): Pair<String, String> {
        // Use FHIR engine to fetch patient data, then return the name and date of birth
        val patientData = patientDetailsViewModel.getPatientData()
        val patientName = patientData.name
        val dob = patientData.dob

        return Pair(patientName, dob)
    }

    private fun fetchChildrenFromFHIR() {
        lifecycleScope.launch(Dispatchers.IO) {
            retrofitCallsFhir.fetchAllQuestionnaireResponses(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        response.body()?.let { responseBody ->
                            val rawResponse = responseBody.string()
                            Log.d("InactivatedPolioVaccineView", "Raw Response body: $rawResponse")

                            if (rawResponse.isNotEmpty()) {
                                try {
                                    val fhirContext = FhirContext.forR4()
                                    val parser = fhirContext.newJsonParser()
                                    val bundle = parser.parseResource(org.hl7.fhir.r4.model.Bundle::class.java, rawResponse)

                                    // Clear list before adding new items
                                    ipvs.clear()

                                    // Extract child data from the bundle
                                    extractChildrenFromBundle(bundle)

                                    // Show/hide views based on the presence of children
                                    runOnUiThread { toggleViews() }
                                } catch (e: Exception) {
                                    Log.e("InactivatedPolioVaccineView", "Error parsing response", e)
                                    runOnUiThread {
                                        Toast.makeText(this@InactivatedPolioVaccineView, "Failed to parse response", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(this@InactivatedPolioVaccineView, "Received an empty response", Toast.LENGTH_SHORT).show()
                                    toggleViews()
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@InactivatedPolioVaccineView, "Failed to fetch data: ${response.message()}", Toast.LENGTH_SHORT).show()
                            toggleViews()
                        }
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    runOnUiThread {
                        Log.e("InactivatedPolioVaccineView", "Error occurred while fetching data", t)
                        Toast.makeText(this@InactivatedPolioVaccineView, "Error occurred: ${t.message}", Toast.LENGTH_SHORT).show()
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
            inactivatedPolioVaccineAdapter.notifyDataSetChanged()
        }
    }

    private fun extractChildrenFromQuestionnaire(questionnaireResponse: QuestionnaireResponse) {
        val responseId = questionnaireResponse.id

        // Check if a child with this ID already exists to avoid duplicates
        if (ipvs.any { it.id == responseId }) {
            Log.d("InactivatedPolioVaccineView", "Child with ID $responseId already exists. Skipping duplicate.")
            return
        }

        var dateGiven: String? = null
        var nextVisit: String? = null

        // Loop through items to extract date information
        for (item in questionnaireResponse.item) {
            for (nestedItem in item.item) { // Access nested items here
                when (nestedItem.linkId) {
                    "d59821e9-7b5f-4331-fd0f-057505fc3c3a" -> {
                        dateGiven = nestedItem.answer.firstOrNull()?.valueDateType?.valueAsString
                    }
                    "5f8b913c-8c08-40cb-bcbf-04731d6d4e49" -> {
                        nextVisit = nestedItem.answer.firstOrNull()?.valueDateType?.valueAsString
                    }
                }
            }
        }

        // Add the IPV data to the list if both dates are available
        if (!dateGiven.isNullOrEmpty() && !nextVisit.isNullOrEmpty()) {
            val ipv = IPV(id = responseId, dateGiven = dateGiven, nextVisit = nextVisit)
            ipvs.add(ipv)
            Log.d("IPVActivity", "Added IPV: $ipv, nextVisit: $nextVisit")
        }
        Log.d("IPV List Size", "Number of IPVs: ${ipvs.size}")
    }


    private fun extractResponseId(rawResponseId: String): String {
        val regex = Regex("QuestionnaireResponse/(\\d+)")
        val matchResult = regex.find(rawResponseId)
        return matchResult?.groupValues?.get(1) ?: rawResponseId
    }

    // Function to toggle visibility of the RecyclerView and noRecordView
    private fun toggleViews() {
        if (ipvs.isEmpty()) {
            childRecyclerView.visibility = View.GONE
            noRecordView.visibility = View.VISIBLE
        } else {
            childRecyclerView.visibility = View.VISIBLE
            noRecordView.visibility = View.GONE
        }
    }
}