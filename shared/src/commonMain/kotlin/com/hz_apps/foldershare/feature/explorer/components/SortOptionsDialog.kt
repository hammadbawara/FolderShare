package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hz_apps.foldershare.core.explorer.model.FileSortOption
import com.hz_apps.foldershare.core.explorer.model.SortDirection
import com.hz_apps.foldershare.core.explorer.model.SortField
import com.hz_apps.foldershare.ui.components.AppAlertDialog
import com.hz_apps.foldershare.ui.components.ScrollableColumn

@Composable
fun SortOptionsDialog(
    currentOption: FileSortOption,
    onDismiss: () -> Unit,
    onApply: (FileSortOption) -> Unit
) {
    var selectedField by remember { mutableStateOf(currentOption.field) }
    var selectedDirection by remember { mutableStateOf(currentOption.direction) }
    var directoriesFirst by remember { mutableStateOf(currentOption.directoriesFirst) }
    var showHiddenFiles by remember { mutableStateOf(currentOption.showHiddenFiles) }
    val scrollState = rememberScrollState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Files By") },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth(),
                state = scrollState
            ) {
                Text(
                    text = "Sort Field",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                SortField.entries.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedField = field }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedField == field),
                            onClick = { selectedField = field }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (field) {
                                SortField.NAME -> "Name"
                                SortField.SIZE -> "Size"
                                SortField.DATE -> "Last Modified Date"
                                SortField.TYPE -> "File Extension / Type"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Order",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                SortDirection.entries.forEach { direction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDirection = direction }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDirection == direction),
                            onClick = { selectedDirection = direction }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (direction == SortDirection.ASCENDING) "Ascending" else "Descending",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { directoriesFirst = !directoriesFirst }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Folders on top",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = directoriesFirst,
                        onCheckedChange = { directoriesFirst = it }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHiddenFiles = !showHiddenFiles }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show hidden files",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showHiddenFiles,
                        onCheckedChange = { showHiddenFiles = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        FileSortOption(
                            field = selectedField,
                            direction = selectedDirection,
                            directoriesFirst = directoriesFirst,
                            showHiddenFiles = showHiddenFiles
                        )
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
