package com.creativem.familia

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var googleButton: Button

    // Lanzador para manejar el resultado del selector de cuentas de Google
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                Log.d("Login", "Inicio de sesión exitoso")
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            } else {
                                Log.e("Login", "Error al autenticar con Firebase: ${task.exception?.message}")
                                Toast.makeText(
                                    this,
                                    "Error al iniciar sesión: ${task.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                } else {
                    Log.e("Login", "No se pudo obtener el ID Token")
                    Toast.makeText(this, "No se pudo obtener el ID Token de Google", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("Login", "Autenticación con Google cancelada o fallida: ${result.resultCode}")
                Toast.makeText(this, "Autenticación con Google cancelada", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("Login", "Error en el proceso de autenticación: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Inicializar el botón de Google Sign-In
        googleButton = findViewById(R.id.buttonGoogleSignIn)

        // Inicializar One-Tap Client
        oneTapClient = Identity.getSignInClient(this)

        // Configurar la solicitud de inicio de sesión con Google
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false) // Mostrar todas las cuentas
                    .build()
            )
            .setAutoSelectEnabled(false) // Evitar selección automática
            .build()

        // Configurar el listener del botón
        googleButton.setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun startGoogleSignIn() {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                try {
                    googleSignInLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    )
                } catch (e: Exception) {
                    Log.e("Login", "Error al lanzar el selector de cuentas: ${e.message}", e)
                    Toast.makeText(this, "Error al abrir el selector: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Login", "Fallo al iniciar sesión con Google: ${exception.message}", exception)
                Toast.makeText(this, "No se pudo iniciar sesión: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onStart() {
        super.onStart()
        // Verificar si el usuario ya está autenticado
        if (auth.currentUser != null) {
            Log.d("Login", "Usuario ya autenticado, redirigiendo a MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}