package com.kabarak.kabarakmhis.pnc.inactivated_polio_vaccine

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.pnc.data_class.IPV
//inactivated polio vaccine adapter class
class InactivatedPolioVaccineAdapter(
    private val ipvs: MutableList<IPV>,
    private val onIPVClick: (String) -> Unit // Lambda function to handle child click
) : RecyclerView.Adapter<InactivatedPolioVaccineAdapter.IPV_ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IPV_ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_child_inactivated_polio_vaccine, parent, false) // view is a LinearLayout
        return IPV_ViewHolder(view)
    }

    override fun onBindViewHolder(holder: IPV_ViewHolder, position: Int) {
        val ipv = ipvs[position]
        Log.d("Adapter", "Binding data: Date Given = ${ipv.dateGiven}, Next Visit = ${ipv.nextVisit}")
        holder.bind(ipv)

        // Set click listener to pass the child's responseId (id)
        holder.itemView.setOnClickListener {
            onIPVClick(ipv.id) // Pass the child's ID (responseId) to the lambda function
        }
    }

    override fun getItemCount(): Int {
        return ipvs.size
    }

    class IPV_ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateGivenTextView: TextView = itemView.findViewById(R.id.tvDateGiven)
        private val nextVisitTextView: TextView = itemView.findViewById(R.id.tvNextVisit)

        fun bind(ipv: IPV) {
            dateGivenTextView.text = ipv.dateGiven
            nextVisitTextView.text = ipv.nextVisit
        }
    }
}