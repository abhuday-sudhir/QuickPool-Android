package com.quickpool.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quickpool.app.data.TokenStore
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.OtpRequestDto
import com.quickpool.app.network.OtpVerifyDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dial codes we support, with the national-number length we expect for each. */
data class Country(val flag: String, val name: String, val dialCode: String, val nationalDigits: Int)

private val COUNTRIES = listOf(
    Country("🇮🇳", "India", "+91", 10),
    Country("🇺🇸", "United States", "+1", 10),
    Country("🇬🇧", "United Kingdom", "+44", 10),
    Country("🇦🇪", "UAE", "+971", 9),
    Country("🇸🇬", "Singapore", "+65", 8),
    Country("🇦🇺", "Australia", "+61", 9)
)

/** How long to make people wait between OTP sends, matching common Indian apps. */
private const val RESEND_COOLDOWN_SECONDS = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: (profileComplete: Boolean) -> Unit) {
    var country by remember { mutableStateOf(COUNTRIES.first()) }
    var nationalNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpRequested by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    // Seconds left before "Resend code" becomes tappable again. The backend rate-limits
    // OTP requests per phone, so offering an instant resend just earns a 429.
    var resendSecondsLeft by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    val scope = rememberCoroutineScope()

    // The backend requires E.164, so we assemble it here instead of making the user type it.
    val e164 = Validation.toE164(country.dialCode, nationalNumber)
    val numberLooksValid = Validation.isNationalNumberValid(nationalNumber, country.nationalDigits)

    fun sendOtp() {
        if (!numberLooksValid || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val response = ApiClient.authApi.requestOtp(OtpRequestDto(e164))
                if (response.isSuccessful) {
                    otpRequested = true
                    resendSecondsLeft = RESEND_COOLDOWN_SECONDS
                } else {
                    errorMessage = apiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun verifyOtp() {
        if (otp.length != 6 || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val response = ApiClient.authApi.verifyOtp(OtpVerifyDto(e164, otp))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenStore.saveTokens(body.userId, body.accessToken, body.refreshToken)
                    onLoginSuccess(body.profileComplete)
                } else {
                    errorMessage = apiError(response.code(), response.errorBody()?.string())
                    otp = ""
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(resendSecondsLeft) {
        if (resendSecondsLeft > 0) {
            delay(1_000)
            resendSecondsLeft--
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("QuickPool", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (!otpRequested) "Share rides across your city."
            else "We sent a 6-digit code to $e164",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (!otpRequested) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    modifier = Modifier.width(130.dp)
                ) {
                    OutlinedTextField(
                        value = "${country.flag}  ${country.dialCode}",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        COUNTRIES.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.flag}  ${c.name}  ${c.dialCode}") },
                                onClick = {
                                    country = c
                                    nationalNumber = nationalNumber.take(c.nationalDigits)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = nationalNumber,
                    onValueChange = { input ->
                        nationalNumber = input.filter { it.isDigit() }.take(country.nationalDigits)
                    },
                    label = { Text("Phone number") },
                    placeholder = { Text("9000000001") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    isError = nationalNumber.isNotEmpty() && !numberLooksValid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { sendOtp() }),
                    modifier = Modifier.weight(1f)
                )
            }

            if (nationalNumber.isNotEmpty() && !numberLooksValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${country.name} numbers are ${country.nationalDigits} digits.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { sendOtp() },
                enabled = numberLooksValid && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (isLoading) "Sending…" else "Continue")
            }
        } else {
            OtpInputField(
                otp = otp,
                onOtpChange = { otp = it },
                onComplete = { verifyOtp() }
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        otpRequested = false
                        otp = ""
                        errorMessage = null
                        resendSecondsLeft = 0
                    }) { Text("Change number") }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { sendOtp() },
                        enabled = resendSecondsLeft == 0
                    ) {
                        Text(
                            if (resendSecondsLeft > 0) "Resend in ${resendSecondsLeft}s"
                            else "Resend code"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Dev build: the code is printed in the backend console.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Surface the backend's own message instead of a bare status code. */
private fun apiError(code: Int, body: String?): String {
    val detail = body?.takeIf { it.isNotBlank() }?.let {
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1)
    }
    return detail ?: when (code) {
        401, 403 -> "That code didn't match. Try again."
        404 -> "No pending code for this number. Request a new one."
        409 -> "Please wait a moment before requesting another code."
        else -> "Something went wrong ($code)."
    }
}
