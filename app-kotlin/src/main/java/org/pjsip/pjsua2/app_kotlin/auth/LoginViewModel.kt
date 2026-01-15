package org.pjsip.pjsua2.app_kotlin.auth

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class LoginState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences =
        application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val repository = LoginRepository()

    private val _username = MutableLiveData("")
    val username: LiveData<String> = _username

    private val _password = MutableLiveData("")
    val password: LiveData<String> = _password

    private val _loginState = MutableLiveData(LoginState.IDLE)
    val loginState: LiveData<LoginState> = _loginState

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _loginData = MutableLiveData<SipDataResponse?>()
    val loginData: LiveData<SipDataResponse?> = _loginData

    init {
        loadCredentials()
    }

    fun onUsernameChange(newUsername: String) {
        _username.value = newUsername
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
    }

    private fun loadCredentials() {
        _username.value = prefs.getString("username", "")
        _password.value = prefs.getString("password", "")
    }

    private fun saveCredentials(user: String, pass: String) {
        prefs.edit().apply {
            putString("username", user)
            putString("password", pass)
            apply()
        }
    }

    fun login() {
        val user = _username.value ?: ""
        val pass = _password.value ?: ""

        if (user.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Username and password cannot be empty"
            return
        }

        _loginState.value = LoginState.LOADING
        _errorMessage.value = null

        viewModelScope.launch {
            val result = repository.login(user, pass)

            // For now, let's also simulate success if API fails due to bad URL
            // This is just to unblock testing if no real server exists yet
            // REMOVE THIS IN PRODUCTION
            if (result.isFailure && result.exceptionOrNull()?.message?.contains("Unable to resolve host") == true) {
                // Simulate success for demo purposes if desired, otherwise handle real failure
                // For strict implementation, we just handle the result as is.
            }

            if (result.isSuccess) {
                _loginData.value = result.getOrNull()?.sipData
                saveCredentials(user, pass)
                _loginState.value = LoginState.SUCCESS
            } else {
                _loginState.value = LoginState.ERROR
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Unknown error"
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.IDLE
        _errorMessage.value = null
        _loginData.value = null
    }
}
