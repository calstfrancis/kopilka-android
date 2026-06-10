package com.kopilka.android.ui.addspending

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val PAD_KEYS = listOf(
    "7", "8", "9",
    "4", "5", "6",
    "1", "2", "3",
    ".", "0", "←",
)

/** Calculator-style number pad. Calls [onValue] with the updated string. */
@Composable
fun AmountPad(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun press(key: String) {
        val new = when (key) {
            "←" -> if (value.isEmpty()) "" else value.dropLast(1)
            "." -> if ("." in value) value else "$value."
            else -> {
                // Prevent leading zeros like "007"
                if (value == "0" && key != ".") key
                else if (value.length >= 10) value
                else value + key
            }
        }
        onValue(new)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PAD_KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { press(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
