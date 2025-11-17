package com.blesense.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.*
import com.google.firebase.auth.GoogleAuthProvider.getCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

// Auth UI States
sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
    data object PasswordResetEmailSent : AuthState()
    data object AccountDeleted : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    // State for authentication flow
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // Current user state
    private val _currentUser = MutableStateFlow(auth.currentUser)
    private lateinit var googleSignInClient: GoogleSignInClient

    init {
        updateCurrentUser()
        auth.addAuthStateListener {
            updateCurrentUser()
        }
    }

    private fun updateCurrentUser() {
        _currentUser.value = auth.currentUser
        auth.currentUser?.let { user ->
            _authState.value = AuthState.Success(user)
        } ?: run {
            _authState.value = AuthState.Idle
        }
    }

    // Check current user
    fun checkCurrentUser(): FirebaseUser? = auth.currentUser

    // Check if user is authenticated
    fun isUserAuthenticated(): Boolean = auth.currentUser != null

    // Send password reset email
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.PasswordResetEmailSent
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthInvalidUserException -> "No account found with this email."
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                    else -> e.message ?: "Failed to send password reset email."
                }
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    // Handle Google sign-in error
    fun handleGoogleSignInError(errorMessage: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Error(errorMessage)
        }
    }

    // Sign in as guest
    fun signInAsGuest() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.signInAnonymously().await()
                result.user?.let {
                    onSignInResult(result)
                    _authState.value = AuthState.Success(it)
                    updateCurrentUser()
                } ?: throw Exception("Anonymous sign-in failed")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    // Register new user
    fun registerUser(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let {
                    onSignInResult(result)
                    _authState.value = AuthState.Success(it)
                    updateCurrentUser()
                } ?: throw Exception("Registration failed. No user created.")
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthWeakPasswordException -> "Weak password: ${e.reason}"
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                    is FirebaseAuthUserCollisionException -> "This email is already registered."
                    else -> e.message ?: "Registration failed due to an unknown error."
                }
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    // Login existing user
    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let {
                    onSignInResult(result)
                    _authState.value = AuthState.Success(it)
                    updateCurrentUser()
                } ?: throw Exception("Login failed. No user found.")
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
                    is FirebaseAuthInvalidUserException -> "No account found with this email."
                    else -> e.message ?: "Login failed due to an unknown error."
                }
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    // Set Google Sign-In client
    fun setGoogleSignInClient(client: GoogleSignInClient) {
        googleSignInClient = client
    }

    // Sign in with Google using ID token
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val credential = getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                result.user?.let {
                    onSignInResult(result)
                    _authState.value = AuthState.Success(it)
                    updateCurrentUser()
                } ?: throw Exception("Google sign-in failed")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message ?: "Google sign-in failed due to an unknown error"
                )
            }
        }
    }


    // Sign out from Firebase
    fun signOut(context: Context) {
        auth.signOut()
        _authState.value = AuthState.Idle
        updateCurrentUser()

        // Also sign out from Google
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut()
    }


    fun deleteAccountAndSignInAsGuest(password: String? = null, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val currentUser = auth.currentUser ?: throw Exception("No user signed in.")
                val currentUserId = currentUser.uid

                // Re-authenticate if password is provided
                password?.let {
                    val credential = EmailAuthProvider.getCredential(currentUser.email!!, it)
                    currentUser.reauthenticate(credential).await()
                }

                // ✅ STEP 1: Sign in anonymously first (start new session)
                val result = auth.signInAnonymously().await()
                val guestUser = result.user ?: throw Exception("Anonymous sign-in failed")

                // ✅ STEP 2: Now safely delete the old user (Firebase won't log you out)
                currentUser.delete().await()

                // ✅ STEP 3: Update local repository
                UserRepository.removeUser(currentUserId)
                UserRepository.addUser(
                    UserData(
                        id = guestUser.uid,
                        name = "Guest",
                        email = "guest@example.com",
                        isAnonymous = true,
                        profilePictureUrl = null
                    )
                )

                updateCurrentUser()
                _authState.value = AuthState.AccountDeleted
                callback(true, null)

            } catch (e: Exception) {
                val message = when (e) {
                    is FirebaseAuthRecentLoginRequiredException -> "Please re-authenticate before deleting your account."
                    is FirebaseAuthInvalidUserException -> "User already deleted."
                    else -> e.message ?: "Account deletion failed."
                }
                _authState.value = AuthState.Error(message)
                callback(false, message)
            }
        }
    }


    // Handle sign-in result and add user to repository
    private fun onSignInResult(authResult: AuthResult) {
        val user = authResult.user
        if (user != null) {
            UserRepository.addUser(
                UserData(
                    id = user.uid,
                    name = user.displayName ?: user.email?.substringBefore('@') ?: "User",
                    email = user.email ?: "",
                    isAnonymous = user.isAnonymous,
                    profilePictureUrl = user.photoUrl?.toString(),
                    signInTime = System.currentTimeMillis()
                )
            )
        }
    }

    // Delete account permanently from Firebase
    fun deleteAccountStayInApp(callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("No user signed in.")

                val deletedUserId = currentUser.uid

                // Delete the current Firebase account
                currentUser.delete().await()

                // Remove the deleted user from local repository
                UserRepository.removeUser(deletedUserId)

                // Sign in anonymously
                val result = auth.signInAnonymously().await()
                val newUser = result.user ?: throw Exception("Anonymous sign-in failed")

                val guestUser = UserData(
                    id = newUser.uid,
                    name = "Guest",
                    email = "guest@example.com",
                    isAnonymous = true,
                    profilePictureUrl = null
                )

                // Add guest user to repository
                UserRepository.addUser(guestUser)

                // Update UI/auth state
                updateCurrentUser()

                callback(true, null)
            } catch (e: Exception) {
                callback(false, e.message)
            }
        }
    }



    // Get saved accounts from repository
    fun getAvailableAccounts(): List<UserData> {
        return UserRepository.users
    }

    // Remove a specific account from repository
    fun removeAccount(userId: String) {
        UserRepository.removeUser(userId)
    }

    // Clear all saved accounts
    fun clearAllAccounts() {
        UserRepository.clearUsers()
    }
}

// Repository to manage user data locally
object UserRepository {
    private val _users = mutableStateListOf<UserData>()

    val users: List<UserData>
        get() = _users.toList()

    @Synchronized
    fun addUser(user: UserData) {
        if (!_users.any { it.id == user.id }) {
            _users.add(user)
        }
    }

    @Synchronized
    fun removeUser(userId: String) {
        _users.removeAll { it.id == userId }
    }

    @Synchronized
    fun clearUsers() {
        _users.clear()
    }
}

data class UserData(
    val id: String,
    val name: String,
    val email: String,
    val isAnonymous: Boolean,
    val profilePictureUrl: String? = null,
    val signInTime: Long = System.currentTimeMillis()
)
