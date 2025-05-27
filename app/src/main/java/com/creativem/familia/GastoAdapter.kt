package com.creativem.familia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.familia.Modelo.Gasto

class GastoAdapter(private var listaGastos: List<Gasto>) :
    RecyclerView.Adapter<GastoAdapter.GastoViewHolder>() {

    class GastoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textValor: TextView = itemView.findViewById(R.id.textValor)
        val textDetalle: TextView = itemView.findViewById(R.id.textDetalle)
        val textFecha: TextView = itemView.findViewById(R.id.textFecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gasto, parent, false)
        return GastoViewHolder(view)
    }

    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gasto = listaGastos[position]
        holder.textValor.text = "Gasto: $${gasto.valor}"
        holder.textDetalle.text = gasto.detalle
        holder.textFecha.text = "Fecha: ${gasto.fecha}"
    }

    override fun getItemCount(): Int = listaGastos.size

    // ✅ Método para actualizar la lista y mostrar los últimos primero
    fun actualizarLista(nuevaLista: List<Gasto>) {
        listaGastos = nuevaLista.reversed() // Invertir para que el último aparezca primero
        notifyDataSetChanged()
    }
}
