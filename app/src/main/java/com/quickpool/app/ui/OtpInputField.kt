package com.quickpool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OtpInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    onComplete: () -> Unit,
    length: Int = 6
) {
    BasicTextField(
        value = otp,
        onValueChange = { new ->
            val digitsOnly = new.filter { it.isDigit() }.take(length)
            onOtpChange(digitsOnly)
            if (digitsOnly.length == length) onComplete()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (otp.length == length) onComplete()
        }),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(length) { index ->
                    val char = otp.getOrNull(index)?.toString() ?: ""
                    val isFocusedSlot = index == otp.length
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isFocusedSlot) 2.dp else 1.dp,
                                color = if (isFocusedSlot) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(char, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    )
}