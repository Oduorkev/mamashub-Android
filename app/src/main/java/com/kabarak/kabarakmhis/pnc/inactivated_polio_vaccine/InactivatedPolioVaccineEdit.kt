package com.kabarak.kabarakmhis.pnc.inactivated_polio_vaccine

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.network_request.requests.RetrofitCallsFhir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.hl7.fhir.r4.model.QuestionnaireResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
//inactivated polio vaccine edit class
class InactivatedPolioVaccineEdit : AppCompatActivity() {

    private lateinit var retrofitCallsFhir: RetrofitCallsFhir
    private var questionnaireJsonString: String? = null
    private lateinit var responseId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inactivated_polio_vaccine_edit)

        retrofitCallsFhir = RetrofitCallsFhir()
        questionnaireJsonString = getStringFromAssets("ipv.json")

        responseId = intent.getStringExtra("responseId") ?: ""
        Log.d("InactivatedPolioVaccineEdit", "Response ID: $responseId")

        if (savedInstanceState == null && questionnaireJsonString != null) {
            renderInitialQuestionnaire()

            CoroutineScope(Dispatchers.IO).launch {
                fetchAndPopulateQuestionnaireResponse(responseId)
            }
        } else {
            Log.e("InactivatedPolioVaccineEdit", "Failed to load questionnaire JSON")
        }

        supportFragmentManager.setFragmentResultListener(
            QuestionnaireFragment.SUBMIT_REQUEST_KEY,
            this
        ) { _, _ ->
            Log.d("InactivatedPolioVaccineEdit", "Submit request received")
            submitUpdatedResponse()
        }
    }

    private fun getStringFromAssets(fileName: String): String? {
        return try {
            val inputStream = assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("InactivatedPolioVaccineEdit", "Error reading asset file $fileName", e)
            null
        }
    }

    private fun renderInitialQuestionnaire() {
        questionnaireJsonString?.let {
            val questionnaireFragment = QuestionnaireFragment.builder()
                .setQuestionnaire(it)
                .build()

            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container_view, questionnaireFragment, "ipv.json")
            }
        } ?: run {
            Log.e("InactivatedPolioVaccineEdit", "Questionnaire JSON is null")
        }
    }

    private suspend fun fetchAndPopulateQuestionnaireResponse(responseId: String) {
        retrofitCallsFhir.fetchQuestionnaireResponse(responseId, object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val questionnaireResponseString = response.body()?.string()
                        if (questionnaireResponseString != null) {
                            try {
                                val fhirContext = FhirContext.forR4()
                                val jsonParser = fhirContext.newJsonParser()
                                val questionnaireResponse = jsonParser.parseResource(
                                    QuestionnaireResponse::class.java,
                                    questionnaireResponseString
                                )

                                CoroutineScope(Dispatchers.Main).launch {
                                    populateQuestionnaireFragment(questionnaireResponse)
                                }
                            } catch (e: Exception) {
                                Log.e("InactivatedPolioVaccineEdit", "Error parsing questionnaire response", e)
                                showToast("Error populating questionnaire")
                            }
                        } else {
                            Log.e("InactivatedPolioVaccineEdit", "Questionnaire response is null")
                            showToast("Failed to retrieve the response data.")
                        }
                    }
                } else {
                    Log.e("InactivatedPolioVaccineEdit", "Failed to fetch response. Response code: ${response.code()}")
                    showToast("Failed to fetch the questionnaire response: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("InactivatedPolioVaccineEdit", "Error occurred while fetching questionnaire response", t)
                showToast("Error occurred while fetching: ${t.message}")
            }
        })
    }

    private fun populateQuestionnaireFragment(questionnaireResponse: QuestionnaireResponse) {
        try {
            val fhirContext = FhirContext.forR4()
            val jsonParser = fhirContext.newJsonParser()
            val questionnaireResponseString = jsonParser.encodeResourceToString(questionnaireResponse)

            questionnaireJsonString?.let {
                val questionnaireFragment = QuestionnaireFragment.builder()
                    .setQuestionnaire(it)
                    .setQuestionnaireResponse(questionnaireResponseString)
                    .build()

                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.fragment_container_view, questionnaireFragment, "populated-questionnaire-fragment")
                }

                Log.d("InactivatedPolioVaccineEdit", "Questionnaire response populated successfully.")
            } ?: run {
                Log.e("InactivatedPolioVaccineEdit", "Questionnaire JSON is null, cannot populate fragment")
            }
        } catch (e: Exception) {
            Log.e("InactivatedPolioVaccineEdit", "Error initializing the questionnaire fragment or ViewModel", e)
            showToast("Error initializing questionnaire")
        }
    }

    private fun submitUpdatedResponse() {
        val fragment = supportFragmentManager.findFragmentByTag("populated-questionnaire-fragment") as? QuestionnaireFragment
        if (fragment == null) {
            showToast("Fragment not found")
            return
        }

        val updatedQuestionnaireResponse = fragment.getQuestionnaireResponse()
        if (updatedQuestionnaireResponse != null) {
            val fhirContext = FhirContext.forR4()
            val jsonParser = fhirContext.newJsonParser()
            val updatedResponseString = jsonParser.encodeResourceToString(updatedQuestionnaireResponse)

            retrofitCallsFhir.updateQuestionnaireResponse(responseId, updatedResponseString, object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        showToast("Data updated successfully.")
                        finish()
                    } else {
                        Log.e("InactivatedPolioVaccineEdit", "Failed to update data. Response code: ${response.code()}")
                        showToast("Failed to update data: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.e("InactivatedPolioVaccineEdit", "Error occurred while updating questionnaire response", t)
                    showToast("Error occurred while updating: ${t.message}")
                }
            })
        } else {
            showToast("Failed to retrieve updated response")
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@InactivatedPolioVaccineEdit, message, Toast.LENGTH_SHORT).show()
        }
    }
}