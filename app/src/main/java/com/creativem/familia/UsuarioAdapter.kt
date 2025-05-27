package com.creativem.familia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.familia.Modelo.Usuario
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsuarioAdapter(private val usuarios: List<Usuario>) :
    RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_usuario, parent, false)
        return UsuarioViewHolder(v)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        val u = usuarios[position]
        holder.nombreText.text = u.nombre
//        holder.correoText.text = u.correo

        val mesActual = obtenerMesActualConAnio()  // Ej: "abril_2025"
        val nombreMesSolo = mesActual.split("_")[0]  // Extrae solo "abril"

        val cuotaDelMes = u.cuotas[mesActual]  // Busca en el mapa usando "abril_2025"

        if (cuotaDelMes != null) {
            holder.cuotaText.text = "$nombreMesSolo: $$cuotaDelMes"
            holder.cuotaText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else {
            holder.cuotaText.text = "$nombreMesSolo: Sin Aporte"
            holder.cuotaText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        }
    }

    private fun obtenerMesActualConAnio(): String {
        val calendar = Calendar.getInstance()
        val mes = SimpleDateFormat("MMMM", Locale("es", "ES")).format(calendar.time).lowercase() // Obtener el mes actual en minúsculas
        val anio = calendar.get(Calendar.YEAR)  // Obtener el año actual
        return "${mes}_${anio}"  // Concatenar mes y año
    }



    override fun getItemCount(): Int = usuarios.size

    class UsuarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nombreText: TextView = itemView.findViewById(R.id.nombreText)
//        val correoText: TextView = itemView.findViewById(R.id.correoText)
        val cuotaText: TextView = itemView.findViewById(R.id.cuotaText)
    }

}