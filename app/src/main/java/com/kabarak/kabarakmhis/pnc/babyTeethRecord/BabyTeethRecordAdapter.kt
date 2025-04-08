package com.kabarak.kabarakmhis.pnc.babyTeethRecord

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.pnc.data_class.BabyTeethRecordDataClass

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BabyTeethRecordAdapter(
    private val babyTeethList: MutableList<BabyTeethRecordDataClass>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<BabyTeethRecordAdapter.BabyTeethViewHolder>() {

    // ViewHolder class for holding views
    inner class BabyTeethViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateSeenTextView: TextView = itemView.findViewById(R.id.text_date_seen)
        private val ageSeenTextView: TextView = itemView.findViewById(R.id.text_age_seen)
        private val teethNumberTextView: TextView = itemView.findViewById(R.id.text_teeth_number)

        init {
            // Set a click listener on the entire item view
            itemView.setOnClickListener {
                onItemClick(babyTeethList[adapterPosition].id)
            }
        }




        fun bind(babyTeethRecord: BabyTeethRecordDataClass) {
            // Define the input format
            val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH)

            // Parse the date
            val date: Date = inputFormat.parse(babyTeethRecord.ageWhenSeen)!!
            // Define output formats for date and time
            val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.ENGLISH)
            val timeFormat = SimpleDateFormat("hh:mm a ", Locale.ENGLISH)

            // Format date and time separately
            val formattedDate = dateFormat.format(date)
            val formattedTime = timeFormat.format(date)
            // Bind data to views
            dateSeenTextView.text = "Age Of Baby: ${babyTeethRecord.dateSeen} Months"
            ageSeenTextView.text = "Date: ${formattedDate}   Time: ${formattedTime} "
            teethNumberTextView.text = "Teeth checked: ${babyTeethRecord.teethType}"
            Log.d("time ", babyTeethRecord.ageWhenSeen)
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BabyTeethViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_baby_teeth_record, parent, false)
        return BabyTeethViewHolder(view)
    }

    override fun onBindViewHolder(holder: BabyTeethViewHolder, position: Int) {
        holder.bind(babyTeethList[position])
    }

    override fun getItemCount(): Int = babyTeethList.size
}
