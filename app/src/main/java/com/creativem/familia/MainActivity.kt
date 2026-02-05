package com.creativem.familia

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.familia.Modelo.Gasto
import com.creativem.familia.Modelo.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var adapter: UsuarioAdapter
    private val listaUsuarios = mutableListOf<Usuario>()

    private lateinit var recyclerGastos: RecyclerView
    private lateinit var gastosAdapter: GastoAdapter
    private var listaGastos: List<Gasto> = listOf()
    private var mesInicioGlobal: String = "sin datos"
    private var mesFinalGlobal: String = "sin datos"



    private val correoAdministrador = "luzcelyrg@gmail.com"

    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var cacheUsuariosSnapshot: DataSnapshot? = null
    private var cacheGastosSnapshot: DataSnapshot? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewUsuarios)
        recyclerView.layoutManager = LinearLayoutManager(this)

        firebaseHelper = FirebaseHelper()

        FirebaseAuth.getInstance().currentUser?.let { user ->
            val nombreUsuario = user.displayName ?: ""
            firebaseHelper.registrarUsuarioSiNoExiste(nombreUsuario)
            cargarUsuarios()
        }

        recyclerGastos = findViewById(R.id.recyclerGastos)
        gastosAdapter = GastoAdapter(listaGastos)
        recyclerGastos.layoutManager = LinearLayoutManager(this)
        recyclerGastos.adapter = gastosAdapter

        cargarGastosDesdeFirebase()
        calcularTotales()
        escucharCambiosEnTotales()

        val auth = FirebaseAuth.getInstance()
        val usuarioActual = auth.currentUser

        val buttonAsignarCuota = findViewById<ImageView>(R.id.buttonAsignarCuota)
        buttonAsignarCuota.setOnClickListener {
            mostrarDialogoAsignarCuota()
        }

        val buttonAsignarGasto = findViewById<ImageView>(R.id.buttonAsignarGasto)
        buttonAsignarGasto.setOnClickListener {
            mostrarDialogoAgregarGasto()
        }

        if (usuarioActual?.email == correoAdministrador) {
            buttonAsignarCuota.visibility = View.VISIBLE
            buttonAsignarGasto.visibility = View.VISIBLE
        } else {
            buttonAsignarCuota.visibility = View.GONE
            buttonAsignarGasto.visibility = View.GONE
        }

        val btnExportarPDF = findViewById<ImageView>(R.id.btnExportarPDF)
        btnExportarPDF.setOnClickListener {
            exportarPDF()
        }


    }

    private fun cargarUsuarios() {
        firebaseHelper.getUsuariosRef().addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {


                listaUsuarios.clear()
                for (ds in snapshot.children) {
                    val uid = ds.key ?: continue
                    val nombre = ds.child("nombre").getValue(String::class.java) ?: ""
                    val correo = ds.child("correo").getValue(String::class.java) ?: ""

                    val cuotas = mutableMapOf<String, Int>()
                    for (cuotaSnapshot in ds.children) {
                        val key = cuotaSnapshot.key ?: continue
                        if (key.matches(Regex("^[a-zA-Z]+_\\d{4}$"))) {
                            val valor = cuotaSnapshot.getValue(Int::class.java)
                            if (valor != null) {
                                cuotas[key] = valor
                            }
                        }
                    }

                    // Reunir todas las claves "mes_año" de todos los usuarios
                    val todasLasFechas = listaUsuarios
                        .flatMap { it.cuotas.keys } // obtener todas las claves de cuotas
                        .distinct()
                        .sortedWith { a, b ->
                            val (mesA, anioA) = a.split("_")
                            val (mesB, anioB) = b.split("_")

                            val mesesOrden = listOf(
                                "enero", "febrero", "marzo", "abril", "mayo", "junio",
                                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
                            )

                            val indexA = mesesOrden.indexOf(mesA.lowercase())
                            val indexB = mesesOrden.indexOf(mesB.lowercase())

                            val anioIntA = anioA.toIntOrNull() ?: 0
                            val anioIntB = anioB.toIntOrNull() ?: 0

                            if (anioIntA != anioIntB) anioIntA - anioIntB else indexA - indexB
                        }

// Guardar como variables globales o usar directamente
                    mesInicioGlobal = todasLasFechas.firstOrNull()?.replace("_", " ") ?: "sin datos"
                    mesFinalGlobal = todasLasFechas.lastOrNull()?.replace("_", " ") ?: "sin datos"


                    val usuario = Usuario(uid = uid, nombre = nombre, correo = correo, cuotas = cuotas)
                    listaUsuarios.add(usuario)
                }

                adapter = UsuarioAdapter(listaUsuarios)
                recyclerView.adapter = adapter

                // Actualiza cache y recalcula totales
                cacheUsuariosSnapshot = snapshot
                calcularTotales(cacheUsuariosSnapshot, cacheGastosSnapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun mostrarDialogoIngresoCuota(nombreUsuario: String, uidUsuario: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Asignar Cuota a $nombreUsuario")
            .setMessage("Ingrese el monto de la cuota:")
            .setView(input)
            .setPositiveButton("Asignar") { _, _ ->
                val cuota = input.text.toString().toIntOrNull()
                if (cuota != null && cuota >= 0) {
                    val mesActual = obtenerMesActualConAnio()
                    val usuarioRef = db.child("usuarios").child(uidUsuario)
                    usuarioRef.child(mesActual).setValue(cuota).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Cuota asignada correctamente", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error al asignar cuota", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Por favor, ingrese un monto válido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoAsignarCuota() {
        val usuarios: List<Pair<String, String>> = listaUsuarios.map { it.nombre to it.uid }
        val nombresUsuarios = usuarios.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Asignar Cuota")
            .setItems(nombresUsuarios) { _, which ->
                val nombreUsuarioSeleccionado = usuarios[which].first
                val uidUsuarioSeleccionado = usuarios[which].second
                mostrarDialogoIngresoCuota(nombreUsuarioSeleccionado, uidUsuarioSeleccionado)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun obtenerMesActualConAnio(): String {
        val calendar = Calendar.getInstance()
        val mes = SimpleDateFormat("MMMM", Locale("es", "ES")).format(calendar.time).lowercase()
        val anio = calendar.get(Calendar.YEAR)
        return "${mes}_${anio}"
    }

    private fun mostrarDialogoAgregarGasto() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val inputValor = EditText(this).apply {
            hint = "Valor del gasto"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val inputDetalle = EditText(this).apply {
            hint = "Detalle del gasto"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        layout.addView(inputValor)
        layout.addView(inputDetalle)

        AlertDialog.Builder(this)
            .setTitle("Registrar Gasto")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val valor = inputValor.text.toString().toIntOrNull()
                val detalle = inputDetalle.text.toString().trim()

                if (valor != null && valor > 0 && detalle.isNotEmpty()) {
                    val gastoRef = db.child("gastos").push()
                    val fecha = obtenerFechaActual()
                    val data = mapOf(
                        "valor" to valor,
                        "detalle" to detalle,
                        "fecha" to fecha
                    )
                    gastoRef.setValue(data).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Gasto guardado correctamente", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error al guardar el gasto", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Complete todos los campos correctamente", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun obtenerFechaActual(): String {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formato.format(Date())
    }

    private fun cargarGastosDesdeFirebase() {
        val ref = db.child("gastos")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaTemporal = mutableListOf<Gasto>()
                for (gastoSnap in snapshot.children) {
                    val gasto = gastoSnap.getValue(Gasto::class.java)
                    gasto?.let { listaTemporal.add(it) }
                }

                // Guardar en la lista global para usarla en exportarPDF()
                listaGastos = listaTemporal

                // Actualizar la lista del adaptador
                gastosAdapter.actualizarLista(listaTemporal)

                // Guardar snapshot en caché y recalcular totales
                cacheGastosSnapshot = snapshot
                calcularTotales(cacheUsuariosSnapshot, cacheGastosSnapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error al cargar gastos", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun calcularTotales(
        usuariosSnapshot: DataSnapshot? = null,
        gastosSnapshot: DataSnapshot? = null
    ) {
        if (usuariosSnapshot == null || gastosSnapshot == null) {
            return
        }

        var totalCuotas = 0
        var totalGastos = 0

        // Calcular total cuotas
        for (usuarioSnap in usuariosSnapshot.children) {
            for (cuotaSnap in usuarioSnap.children) {
                val key = cuotaSnap.key ?: continue
                if (key.contains("_")) {
                    val valorCuota = cuotaSnap.getValue(Int::class.java) ?: 0
                    totalCuotas += valorCuota
                }
            }
        }

        // Calcular total gastos
        for (gastoSnap in gastosSnapshot.children) {
            val valorGasto = gastoSnap.child("valor").getValue(Int::class.java) ?: 0
            totalGastos += valorGasto
        }

        val saldoFinal = totalCuotas - totalGastos
        actualizarTotalesEnUI(totalCuotas, totalGastos, saldoFinal)
    }

    private fun actualizarTotalesEnUI(totalCuotas: Int, totalGastos: Int, saldoFinal: Int) {
        val tvTotalCuotas = findViewById<TextView>(R.id.tvTotalCuotas)
        val tvTotalGastos = findViewById<TextView>(R.id.tvTotalGastos)
        val tvSaldoFinal = findViewById<TextView>(R.id.tvSaldoFinal)

        tvTotalCuotas.text = "Aporte: $totalCuotas"
        tvTotalGastos.text = "Gasto: $totalGastos"
        tvSaldoFinal.text = "Saldo: $saldoFinal"
    }

    private fun escucharCambiosEnTotales() {
        db.child("usuarios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cacheUsuariosSnapshot = snapshot
                calcularTotales(cacheUsuariosSnapshot, cacheGastosSnapshot)
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        db.child("gastos").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cacheGastosSnapshot = snapshot
                calcularTotales(cacheUsuariosSnapshot, cacheGastosSnapshot)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun exportarPDF() {

        if (listaUsuarios.isEmpty()) {
            Toast.makeText(this, "No hay datos", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()

        val pageWidth = 842
        val pageHeight = 595
        val margin = 25f
        val rowHeight = 24f

        val mesesKeys = listOf(
            "enero","febrero","marzo","abril","mayo","junio",
            "julio","agosto","septiembre","octubre","noviembre","diciembre"
        )

        val mesesLabel = listOf(
            "Ene","Feb","Mar","Abr","May","Jun",
            "Jul","Ago","Sep","Oct","Nov","Dic"
        )

        val textPaint = Paint().apply { textSize = 9f }
        val mesPaint = Paint().apply { textSize = 7f; textAlign = Paint.Align.CENTER }
        val numberPaint = Paint().apply { textSize = 7.5f }

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val titlePaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        val fondoVerde = Paint().apply { style = Paint.Style.FILL; color = Color.rgb(200,255,200) }
        val fondoRojo = Paint().apply { style = Paint.Style.FILL; color = Color.rgb(255,220,220) }
        val fondoBalance = Paint().apply { style = Paint.Style.FILL; color = Color.rgb(240,240,240) }

        fun money(valor: Int) =
            "$" + String.format("%,d", valor).replace(",", ".")

        val años = listaUsuarios.flatMap { it.cuotas.keys }
            .mapNotNull { it.split("_").getOrNull(1) }
            .distinct()
            .sorted()

        var saldoAnterior = 0
        var pageNumber = 1

        for (año in años) {

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var y = margin

            // TITULO
            val titulo = "Familia Contabilidad - Año $año"
            val subtitulo = "Aporte para Salud Mamá"

            canvas.drawText(titulo,(pageWidth-titlePaint.measureText(titulo))/2f,y,titlePaint)
            y += 18f
            canvas.drawText(subtitulo,(pageWidth-textPaint.measureText(subtitulo))/2f,y,textPaint)
            y += 25f

            val anchoDisponible = pageWidth - margin * 2

            var maxNombreWidth = textPaint.measureText("Nombre")
            listaUsuarios.forEach {
                maxNombreWidth = max(maxNombreWidth, textPaint.measureText(it.nombre))
            }

            val colNombre = (maxNombreWidth + 20f).coerceAtMost(anchoDisponible * 0.35f)
            val espacioRestante = anchoDisponible - colNombre
            val colMes = espacioRestante / 13f
            val colTotal = colMes

            var x = margin

            // CABECERA
            canvas.drawRect(x,y,x+colNombre,y+rowHeight,fondoBalance)
            canvas.drawRect(x,y,x+colNombre,y+rowHeight,borderPaint)
            canvas.drawText("Nombre",x+colNombre/2-textPaint.measureText("Nombre")/2,y+16f,textPaint)
            x+=colNombre

            mesesLabel.forEach {
                canvas.drawRect(x,y,x+colMes,y+rowHeight,borderPaint)
                canvas.drawText(it,x+colMes/2,y+16f,mesPaint)
                x+=colMes
            }

            canvas.drawRect(x,y,x+colTotal,y+rowHeight,borderPaint)
            canvas.drawText("Total",x+colTotal/2,y+16f,mesPaint)

            y += rowHeight

            var totalRecaudadoAño = 0

            listaUsuarios.forEach { usuario ->
                x = margin
                var totalUsuario = 0

                canvas.drawRect(x,y,x+colNombre,y+rowHeight,fondoBalance)
                canvas.drawRect(x,y,x+colNombre,y+rowHeight,borderPaint)
                canvas.drawText(usuario.nombre,x+colNombre/2-textPaint.measureText(usuario.nombre)/2,y+16f,textPaint)
                x+=colNombre

                mesesKeys.forEach { mes ->
                    val valor = usuario.cuotas["${mes}_$año"] ?: 0
                    totalUsuario += valor
                    totalRecaudadoAño += valor

                    val fondo = if(valor>0) fondoVerde else fondoRojo

                    canvas.drawRect(x,y,x+colMes,y+rowHeight,fondo)
                    canvas.drawRect(x,y,x+colMes,y+rowHeight,borderPaint)

                    val txt = money(valor)
                    canvas.drawText(txt,x+colMes/2-numberPaint.measureText(txt)/2,y+15f,numberPaint)
                    x+=colMes
                }

                val fondoTotal = if(totalUsuario>0) fondoVerde else fondoRojo

                canvas.drawRect(x,y,x+colTotal,y+rowHeight,fondoTotal)
                canvas.drawRect(x,y,x+colTotal,y+rowHeight,borderPaint)

                val txtTotal = money(totalUsuario)
                canvas.drawText(txtTotal,x+colTotal/2-numberPaint.measureText(txtTotal)/2,y+15f,numberPaint)

                y+=rowHeight
            }

            val totalGastosAño = listaGastos
                .filter { it.fecha?.contains(año)==true }
                .sumOf { it.valor ?: 0 }

            val saldoActual = saldoAnterior + totalRecaudadoAño - totalGastosAño

            y+=30f

            canvas.drawRect(margin,y,pageWidth-margin,y+85f,fondoBalance)

            val balancePaint = Paint().apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            canvas.drawText("Balance año $año",margin+10f,y+18f,balancePaint)
            canvas.drawText("Saldo anterior: ${money(saldoAnterior)}",margin+10f,y+35f,numberPaint)
            canvas.drawText("Total recaudado: ${money(totalRecaudadoAño)}",margin+10f,y+50f,numberPaint)
            canvas.drawText("Total gastos: ${money(totalGastosAño)}",margin+10f,y+65f,numberPaint)

            val txtDisponible="Disponible actual: ${money(saldoActual)}"
            canvas.drawText(txtDisponible,(pageWidth-balancePaint.measureText(txtDisponible))/2f,y+82f,balancePaint)

            saldoAnterior=saldoActual

            // ===== GASTOS MULTIPAGINA REAL =====
            y+=110f

            val gastosDelAño=listaGastos.filter { it.fecha?.contains(año)==true }

            if(gastosDelAño.isNotEmpty()){

                val subtituloPaint=Paint().apply{
                    textSize=13f
                    typeface=Typeface.DEFAULT_BOLD
                }

                canvas.drawText("Detalle gastos año $año",margin,y,subtituloPaint)
                y+=25f

                for(gasto in gastosDelAño){

                    if(y>pageHeight-40f){

                        pdfDocument.finishPage(page)

                        pageInfo=PdfDocument.PageInfo.Builder(pageWidth,pageHeight,pageNumber++).create()
                        page=pdfDocument.startPage(pageInfo)
                        canvas=page.canvas
                        y=margin+20f

                        canvas.drawText("Detalle gastos año $año (cont.)",margin,margin,subtituloPaint)
                    }

                    canvas.drawRect(margin,y-15f,pageWidth-margin,y+8f,fondoBalance)

                    canvas.drawText(gasto.detalle ?: "Sin descripción",margin+5f,y,textPaint)
                    canvas.drawText(gasto.fecha ?: "",pageWidth-margin-170f,y,numberPaint)
                    canvas.drawText(money(gasto.valor ?: 0),pageWidth-margin-60f,y,numberPaint)

                    y+=22f
                }

            }else{
                canvas.drawText("Sin gastos registrados",margin,y,textPaint)
            }

            pdfDocument.finishPage(page)
        }

        val file=File(cacheDir,"Contabilidad_Salud_Mama.pdf")

        try{
            pdfDocument.writeTo(FileOutputStream(file))

            Toast.makeText(this,"PDF generado correctamente",Toast.LENGTH_SHORT).show()

            val uri=FileProvider.getUriForFile(this,"$packageName.provider",file)

            startActivity(Intent(Intent.ACTION_SEND).apply{
                type="application/pdf"
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })

        }catch(e:Exception){
            Toast.makeText(this,"Error: ${e.message}",Toast.LENGTH_LONG).show()
        }finally{
            pdfDocument.close()
        }
    }



}
