package com.kabarak.kabarakmhis.pnc.babyTeethRecord

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import com.kabarak.kabarakmhis.R
import kotlinx.coroutines.launch
import android.widget.Toast
import ca.uhn.fhir.context.FhirContext
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.kabarak.kabarakmhis.network_request.requests.RetrofitCallsFhir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.ResponseBody
import org.hl7.fhir.r4.model.QuestionnaireResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BabyTeethEdit : AppCompatActivity() {
    private lateinit var retrofitCallsFhir: RetrofitCallsFhir
    private var questionnaireJsonString: String? = null
    private lateinit var responseId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_baby_teeth_edit)

        retrofitCallsFhir = RetrofitCallsFhir()
        questionnaireJsonString = getStringFromAssets("BabyTeethRecords.json")
        responseId = intent.getStringExtra("responseId") ?: ""

        if (savedInstanceState == null && questionnaireJsonString != null) {
            renderInitialQuestionnaire()
            CoroutineScope(Dispatchers.IO).launch {
                fetchAndPopulateQuestionnaireResponse(responseId)
            }
        }

        supportFragmentManager.setFragmentResultListener(
            QuestionnaireFragment.SUBMIT_REQUEST_KEY,
            this,
        ) { _, _ ->
            Log.d("BabyTeethEdit", "Submit request received")
            submitUpdatedResponse()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun getStringFromAssets(fileName: String): String? {
        return try {
            val inputStream = assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer)
        } catch (e: Exception) {
            Log.e("BabyTeethEdit", "Error reading file $fileName", e)
            null
        }
    }

    private fun renderInitialQuestionnaire() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(
                R.id.fragment_container_view,
                QuestionnaireFragment.builder()
                    .setQuestionnaire(questionnaireJsonString!!)
                    .build()
            )
        }
    }

    private suspend fun fetchAndPopulateQuestionnaireResponse(responseId: String) {
        retrofitCallsFhir.fetchQuestionnaireResponse(responseId, object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val questionnaireResponseString = response.body()?.string()
                    if (questionnaireResponseString != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val fhirContext = FhirContext.forR4()
                                val jsonParser = fhirContext.newJsonParser()
                                val questionnaireResponse = jsonParser.parseResource(
                                    QuestionnaireResponse::class.java, questionnaireResponseString
                                )
                                populateQuestionnaireFragment(questionnaireResponse)
                            } catch (e: Exception) {
                                Log.e("BabyTeethEdit", "Error populating questionnaire", e)
                                Toast.makeText(this@BabyTeethEdit, "Error populating questionnaire", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        showToast("Failed to retrieve the response data.")
                    }
                } else {
                    showToast("Failed to fetch the questionnaire response: ${response.message()}")
                    Log.e("BabyTeethEdit", "Failed to fetch response. Response code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                showToast("Error occurred while fetching: ${t.message}")
                Log.e("Error", "Error occurred while fetching questionnaire response", t)
            }
        })
    }

    private fun populateQuestionnaireFragment(questionnaireResponse: QuestionnaireResponse) {
        try {
            val fhirContext = FhirContext.forR4()
            val jsonParser = fhirContext.newJsonParser()
            val questionnaireResponseString = jsonParser.encodeResourceToString(questionnaireResponse)

            val questionnaireFragment = QuestionnaireFragment.builder()
                .setQuestionnaire(questionnaireJsonString!!)
                .setQuestionnaireResponse(questionnaireResponseString)
                .build()

            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container_view, questionnaireFragment, "populated-questionnaire-fragment")
            }

            Log.d("BabyTeethEdit", "Questionnaire response populated successfully.")
        } catch (e: Exception) {
            Log.e("BabyTeethEdit", "Error initializing the questionnaire fragment", e)
            Toast.makeText(this, "Error initializing questionnaire", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitUpdatedResponse() {
        val fragment = supportFragmentManager.findFragmentByTag("populated-questionnaire-fragment") as? QuestionnaireFragment
        val updatedQuestionnaireResponse = fragment?.getQuestionnaireResponse()

        if (updatedQuestionnaireResponse != null) {
            val fhirContext = FhirContext.forR4()
            val jsonParser = fhirContext.newJsonParser()
            val updatedResponseString = jsonParser.encodeResourceToString(updatedQuestionnaireResponse)

            retrofitCallsFhir.updateQuestionnaireResponse(responseId, updatedResponseString, object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@BabyTeethEdit, "Data updated successfully.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@BabyTeethEdit, BabyTeethDevelopmentRecord::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        showToast("Failed to update data:")
                        Log.d("Error Update data message", "$response")
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    showToast("Error occurred while updating: ${t.message}")
                }
            })
        } else {
            showToast("Failed to retrieve updated response")
        }
    }
    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@BabyTeethEdit, message, Toast.LENGTH_SHORT).show()
        }
    }
}
