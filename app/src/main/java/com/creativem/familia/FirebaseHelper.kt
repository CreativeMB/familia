package com.creativem.familia

import android.util.Log
import com.creativem.familia.Modelo.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class FirebaseHelper {
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val currentUser = FirebaseAuth.getInstance().currentUser

    fun registrarUsuarioSiNoExiste(nombre: String) {
        currentUser?.let {
            val uid = it.uid
            val correo = it.email ?: ""

            db.child("usuarios").child(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            val nuevoUsuario = Usuario(uid = uid, nombre = nombre, correo = correo)
                            db.child("usuarios").child(uid).setValue(nuevoUsuario)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Firebase", "Error al registrar usuario: ${error.message}")
                    }
                })
        }
    }


    fun getUsuariosRef(): DatabaseReference {
        return db.child("usuarios")
    }
}