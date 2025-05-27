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

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var adapter: UsuarioAdapter
    private val listaUsuarios = mutableListOf<Usuario>()

    private lateinit var recyclerGastos: RecyclerView
    private lateinit var gastosAdapter: GastoAdapter
    private var listaGastos: List<Gasto> = listOf()


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

        val buttonAsignarCuota = findViewById<Button>(R.id.buttonAsignarCuota)
        buttonAsignarCuota.setOnClickListener {
            mostrarDialogoAsignarCuota()
        }
        val buttonAsignarGasto = findViewById<Button>(R.id.buttonAsignarGasto)
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
                        if (key.contains("_")) {
                            val valor = cuotaSnapshot.getValue(Int::class.java)
                            if (valor != null) {
                                cuotas[key] = valor
                            }
                        }
                    }
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

        tvTotalCuotas.text = "Total Cuotas: $totalCuotas"
        tvTotalGastos.text = "Total Gastos: $totalGastos"
        tvSaldoFinal.text = "Saldo Final: $saldoFinal"
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
        if (listaUsuarios.isEmpty() && listaGastos.isEmpty()) {
            Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val rowHeight = 30f
        var pageNumber = 1

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.DEFAULT
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.BLACK
        }

        var y = margin

        // --- Título ---
        canvas.drawText("Reporte de Usuarios, Cuotas y Gastos", margin, y, titlePaint)
        y += 40f

        // --- Tabla Usuarios ---
        val colWidthsUsuarios = listOf(220f, 80f, 100f) // Usuario, Mes, Cuota
        val xStartUsuarios = margin

        // Cabecera tabla Usuarios
        var x = xStartUsuarios
        val headersUsuarios = listOf("Usuario", "Mes", "Cuota")
        for (i in headersUsuarios.indices) {
            canvas.drawRect(x, y, x + colWidthsUsuarios[i], y + rowHeight, borderPaint)
            canvas.drawText(headersUsuarios[i], x + 5f, y + 20f, paint)
            x += colWidthsUsuarios[i]
        }
        y += rowHeight

        // Obtener meses únicos ordenados (alfabéticamente, adapta si necesitas orden cronológico)
        val todosLosMeses = listaUsuarios
            .flatMap { it.cuotas.keys }
            .distinct()
            .sorted()

        for (mes in todosLosMeses) {
            // Título de mes (como fila separadora)
            if (y + rowHeight > pageHeight - margin) {
                pdfDocument.finishPage(page)
                pageNumber++
                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                y = margin
            }
            // Dibujar fila con el mes (columna 1)
            x = xStartUsuarios
            canvas.drawRect(x, y, x + colWidthsUsuarios[0] + colWidthsUsuarios[1] + colWidthsUsuarios[2], y + rowHeight, borderPaint)
            canvas.drawText("Mes: $mes", x + 5f, y + 20f, titlePaint)
            y += rowHeight

            for (usuario in listaUsuarios) {
                if (y + rowHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(newPageInfo)
                    canvas = page.canvas
                    y = margin
                }

                x = xStartUsuarios
                // Columna Usuario
                canvas.drawRect(x, y, x + colWidthsUsuarios[0], y + rowHeight, borderPaint)
                canvas.drawText(usuario.nombre, x + 5f, y + 20f, paint)
                x += colWidthsUsuarios[0]

                // Columna Mes
                canvas.drawRect(x, y, x + colWidthsUsuarios[1], y + rowHeight, borderPaint)
                canvas.drawText(mes, x + 5f, y + 20f, paint)
                x += colWidthsUsuarios[1]

                // Columna Cuota
                canvas.drawRect(x, y, x + colWidthsUsuarios[2], y + rowHeight, borderPaint)
                val cuota = usuario.cuotas[mes]
                val cuotaTexto = if (cuota != null && cuota > 0) "$cuota" else "No dio aporte"
                canvas.drawText(cuotaTexto, x + 5f, y + 20f, paint)

                y += rowHeight
            }
        }

        // --- Espacio antes tabla Gastos ---
        y += 30f

        // Tabla Gastos
        val colWidthsGastos = listOf(220f, 100f, 100f)
        val xStartGastos = margin

        if (y + rowHeight > pageHeight - margin) {
            pdfDocument.finishPage(page)
            pageNumber++
            val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(newPageInfo)
            canvas = page.canvas
            y = margin
        }

        canvas.drawText("Gastos:", xStartGastos, y, titlePaint)
        y += rowHeight

        x = xStartGastos
        val headersGastos = listOf("Detalle", "Valor", "Fecha")
        for (i in headersGastos.indices) {
            canvas.drawRect(x, y, x + colWidthsGastos[i], y + rowHeight, borderPaint)
            canvas.drawText(headersGastos[i], x + 5f, y + 20f, paint)
            x += colWidthsGastos[i]
        }
        y += rowHeight

        if (listaGastos.isEmpty()) {
            if (y + rowHeight > pageHeight - margin) {
                pdfDocument.finishPage(page)
                pageNumber++
                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                y = margin
            }
            canvas.drawText("No hay gastos registrados.", xStartGastos, y + 20f, paint)
            y += rowHeight
        } else {
            for (gasto in listaGastos) {
                if (y + rowHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(newPageInfo)
                    canvas = page.canvas
                    y = margin
                }

                x = xStartGastos
                val detalle = gasto.detalle ?: "Sin detalle"
                val valor = gasto.valor ?: 0
                val fecha = gasto.fecha ?: ""

                canvas.drawRect(x, y, x + colWidthsGastos[0], y + rowHeight, borderPaint)
                canvas.drawText(detalle, x + 5f, y + 20f, paint)
                x += colWidthsGastos[0]

                canvas.drawRect(x, y, x + colWidthsGastos[1], y + rowHeight, borderPaint)
                canvas.drawText(valor.toString(), x + 5f, y + 20f, paint)
                x += colWidthsGastos[1]

                canvas.drawRect(x, y, x + colWidthsGastos[2], y + rowHeight, borderPaint)
                canvas.drawText(fecha, x + 5f, y + 20f, paint)

                y += rowHeight
            }
        }

        // Finaliza la última página
        pdfDocument.finishPage(page)

        // Guardar y compartir (usa tu método de preferencia)
        val fileName = "reporte_familia_${System.currentTimeMillis()}.pdf"
        val file = File(cacheDir, fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "PDF generado exitosamente", Toast.LENGTH_SHORT).show()

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir PDF"))

        } catch (e: Exception) {
            Toast.makeText(this, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }



}
