// Import statements for Android, Google Sign-In, and logging utilities
import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

// Singleton object to handle Google Sign-In functionality
object GoogleSignInHelper {
    // Function to create and return a GoogleSignInClient instance
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        // Get instance of GoogleApiAvailability to check Play Services status
        val googleApiAvailability = GoogleApiAvailability.getInstance()

        // Check if Google Play Services is available on the device
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        // Handle case where Google Play Services is unavailable
        if (resultCode != ConnectionResult.SUCCESS) {
            // Log error with the specific result code
            Log.e("GoogleSignIn", "Google Play Services unavailable: $resultCode")

            // Check if the error is resolvable by the user
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                // If context is an Activity, show error dialog to the user
                (context as? Activity)?.let {
                    googleApiAvailability.getErrorDialog(it, resultCode, 9000)?.show()
                }
            }
            // Throw exception to indicate failure
            throw IllegalStateException("Google Play Services unavailable: $resultCode")
        }

        // Define the web client ID for Google Sign-In
        val webClientId = "634409104545-t9obf7nmakk2jhahr31jlaspva4858fb.apps.googleusercontent.com"

        // Configure Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId) // Request ID token for authentication
            .requestEmail() // Request user's email address
            .build()

        // Create and return GoogleSignInClient with configured options
        return GoogleSignIn.getClient(context, gso)
    }
}