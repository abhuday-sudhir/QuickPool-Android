package com.quickpool.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.UpdateProfileDto
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(onRegistered: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val nameValid = Validation.isNameValid(name)
    val emailValid = Validation.isEmailValid(email)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun save() {
        if (!nameValid || !emailValid || isSaving) return
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                val response = ApiClient.userApi.updateProfile(
                    UpdateProfileDto(name = name.trim(), email = email.trim())
                )
                if (response.isSuccessful) onRegistered()
                else errorMessage = apiMessage(response.code(), response.errorBody()?.string())
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Almost there", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your name is shown to the people you share rides with.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 80) name = it },
            label = { Text("Full name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            isError = name.isNotEmpty() && !nameValid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { if (it.length <= 160) email = it },
            label = { Text("Email") },
            supportingText = { Text("We'll send ride updates and offers here") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            isError = email.isNotEmpty() && !emailValid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (email.isNotEmpty() && !emailValid) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "That email doesn't look right.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { save() },
            enabled = nameValid && emailValid && !isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (isSaving) "Saving…" else "Finish")
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun apiMessage(code: Int, body: String?): String {
    val detail = body?.takeIf { it.isNotBlank() }?.let {
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1)
    }
    return detail ?: "Couldn't save your details ($code)."
}
