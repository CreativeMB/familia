package com.creativem.familia


import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.familia.Modelo.Gasto
import com.creativem.familia.Modelo.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var adapter: UsuarioAdapter
    private val listaUsuarios = mutableListOf<Usuario>()

    private lateinit var recyclerGastos: RecyclerView
    private lateinit var gastosAdapter: GastoAdapter
    private val listaGastos = mutableListOf<Gasto>()

    private val correoAdministrador = "luzcelyrg@gmail.com"

    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
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
        } ?: run {
            // Redirigir al login si no hay usuario autenticado
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
            // Mostrar los botones si es el administrador
            buttonAsignarCuota.visibility = View.VISIBLE
            buttonAsignarGasto.visibility = View.VISIBLE
        } else {
            // Ocultar los botones si no es el administrador
            buttonAsignarCuota.visibility = View.GONE
            buttonAsignarGasto.visibility = View.GONE
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
                        if (key.contains("_")) { // Solo claves tipo "abril_2025"
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

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Asignar Cuota a $nombreUsuario")
        builder.setMessage("Ingrese el monto de la cuota:")
        builder.setView(input)

        builder.setPositiveButton("Asignar") { _, _ ->
            val cuota = input.text.toString().toIntOrNull()
            if (cuota != null && cuota >= 0) {
                // Verificar si el usuario ya tiene una cuota asignada
                val mesActual = obtenerMesActualConAnio()  // Obtener mes y año actual
                val usuarioRef = db.child("usuarios").child(uidUsuario)

                // Actualizar la cuota para el mes actual con la clave mes_año (ej: abril_2025)
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

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun mostrarDialogoAsignarCuota() {
        // Especificar el tipo explícitamente al mapear los usuarios
        val usuarios: List<Pair<String, String>> = listaUsuarios.map { it.nombre to it.uid }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Asignar Cuota")

        // Convertir los nombres de usuarios a un arreglo de Strings
        val nombresUsuarios = usuarios.map { it.first }.toTypedArray()

        builder.setItems(nombresUsuarios) { _, which ->
            val nombreUsuarioSeleccionado = usuarios[which].first
            val uidUsuarioSeleccionado = usuarios[which].second
            mostrarDialogoIngresoCuota(nombreUsuarioSeleccionado, uidUsuarioSeleccionado)
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun obtenerMesActualConAnio(): String {
        val calendar = Calendar.getInstance()
        val mes = SimpleDateFormat("MMMM", Locale("es", "ES")).format(calendar.time).lowercase() // Obtener el mes actual en minúsculas
        val anio = calendar.get(Calendar.YEAR)  // Obtener el año actual
        return "${mes}_${anio}"  // Concatenar mes y año (ej: "abril_2025")
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

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Registrar Gasto")
        builder.setView(layout)

        builder.setPositiveButton("Guardar") { _, _ ->
            val valor = inputValor.text.toString().toIntOrNull()
            val detalle = inputDetalle.text.toString().trim()

            if (valor != null && valor > 0 && detalle.isNotEmpty()) {
                val dbRef = FirebaseDatabase.getInstance().reference
                val gastoRef = dbRef.child("gastos").push()

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

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }
    private fun obtenerFechaActual(): String {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formato.format(Date())
    }


    private fun cargarGastosDesdeFirebase() {
        val ref = FirebaseDatabase.getInstance().getReference("gastos")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaGastos.clear()
                for (gastoSnap in snapshot.children) {
                    val gasto = gastoSnap.getValue(Gasto::class.java)
                    gasto?.let { listaGastos.add(it) }
                }
                gastosAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error al cargar gastos", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private var cacheUsuariosSnapshot: DataSnapshot? = null
    private var cacheGastosSnapshot: DataSnapshot? = null

    private fun calcularTotales(
        usuariosSnapshot: DataSnapshot? = null,
        gastosSnapshot: DataSnapshot? = null
    ) {
        val mesActual = obtenerMesActualConAnio()

        // Cache para evitar que se reinicien los datos cuando llegan asincrónicamente
        if (usuariosSnapshot != null) cacheUsuariosSnapshot = usuariosSnapshot
        if (gastosSnapshot != null) cacheGastosSnapshot = gastosSnapshot

        val usuarios = cacheUsuariosSnapshot
        val gastos = cacheGastosSnapshot

        if (usuarios != null && gastos != null) {
            var totalAportes = 0
            var totalGastos = 0

            for (userSnapshot in usuarios.children) {
                val aporte = userSnapshot.child(mesActual).getValue(Int::class.java)
                if (aporte != null) totalAportes += aporte
            }

            for (gastoSnapshot in gastos.children) {
                val valor = gastoSnapshot.child("valor").getValue(Int::class.java)
                if (valor != null) totalGastos += valor
            }

            val diferencia = totalAportes - totalGastos

            // Actualiza los TextViews
            findViewById<TextView>(R.id.textTotalAportes).text = "Aportes $${totalAportes}"
            findViewById<TextView>(R.id.textTotalGastos).text = "Gastos $${totalGastos}"
            findViewById<TextView>(R.id.textDiferencia).text = "Saldo $${diferencia}"
        }
    }


    private fun escucharCambiosEnTotales() {
        val dbRef = FirebaseDatabase.getInstance().reference
        val mesActual = obtenerMesActualConAnio()

        // Escuchar cambios en "usuarios"
        dbRef.child("usuarios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                calcularTotales(snapshot, null)
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        // Escuchar cambios en "gastos"
        dbRef.child("gastos").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                calcularTotales(null, snapshot)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


}


