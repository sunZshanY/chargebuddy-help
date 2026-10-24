package com.powerbank.finder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.powerbank.finder.R
import com.powerbank.finder.model.Station
import java.util.Locale

class StationAdapter(
    private val items: List<Station>,
    private val onClick: (Station) -> Unit
) : RecyclerView.Adapter<StationAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val meta: TextView = view.findViewById(R.id.tvMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.name.text = s.name
        val status = if (s.available > 0) "有货 ${s.available}/${s.total}" else "已借空"
        holder.meta.text = String.format(
            Locale.CHINA, "%.0f 米 · %s", s.distanceM, status
        )
        holder.itemView.setOnClickListener { onClick(s) }
    }

    override fun getItemCount(): Int = items.size
}