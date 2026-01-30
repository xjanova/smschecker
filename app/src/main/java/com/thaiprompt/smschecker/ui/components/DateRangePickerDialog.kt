package com.thaiprompt.smschecker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startMillis: Long, endMillis: Long) -> Unit
) {
    var step by remember { mutableIntStateOf(0) } // 0 = from, 1 = to
    var startMillis by remember { mutableLongStateOf(0L) }

    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        if (step == 0) {
                            startMillis = selected
                            step = 1
                        } else {
                            val endOfDay = selected + 24 * 60 * 60 * 1000 - 1
                            onDateRangeSelected(startMillis, endOfDay)
                        }
                    }
                }
            ) {
                Text(if (step == 0) "Next" else "Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = if (step == 0) "Select Start Date" else "Select End Date",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
            }
        )
    }
}
