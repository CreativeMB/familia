package com.creativem.familia
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.api.ApiException

class Login : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var mDatabase: DatabaseReference

    private val RC_SIGN_IN = 9001 // Request code for Google Sign-In

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance().reference

        // Verificar si ya hay un usuario autenticado
        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            checkUserProfile(currentUser) // Ir directamente a la app si ya está logueado
            return // No seguir cargando la UI de login
        }

        // Configura el cliente de Google Sign-In
        val googleSignInOptions = com.google.android.gms.auth.api.signin.GoogleSignInOptions
            .Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)

        val signInCard = findViewById<CardView>(R.id.cardGoogleSignIn)
        signInCard.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Error al autenticar con Google: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Inicio de sesión exitoso
                    val user = mAuth.currentUser
                    if (user != null) {
                        checkUserProfile(user)
                    }
                } else {
                    Toast.makeText(this, "Autenticación fallida", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserProfile(user: FirebaseUser) {
        val userRef = mDatabase.child("usuarios").child(user.uid)
        userRef.get().addOnSuccessListener {
            if (it.exists()) {
                // Ya existe, ir a MainActivity
                navigateToOrders()
            } else {
                // Registrar en base de datos con FirebaseHelper
                val nombre = user.displayName ?: "Sin nombre"
                val helper = FirebaseHelper()
                helper.registrarUsuarioSiNoExiste(nombre)

                navigateToOrders()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al verificar perfil: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }




    private fun navigateToOrders() {
        // Redirige al usuario a la página de pedidos
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Finaliza la actividad actual
    }
}
