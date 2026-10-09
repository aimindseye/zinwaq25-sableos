/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.leisure.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogManagerScreen(
    onBack: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    val isEink by viewModel.isEinkEnabled.collectAsStateWithLifecycle()

    // Dialog State
    var showDialog by remember { mutableStateOf(false) }
    var editingCatalog by remember { mutableStateOf<OpdsEntity?>(null) }

    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground

    TidScreen(Tid.Screen.catalogManager) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Catalogs") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.tid(Tid.CatalogManager.back)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCatalog = null
                    showDialog = true
                },
                containerColor = if(isEink) Color.Black else MaterialTheme.colorScheme.primary,
                contentColor = if(isEink) Color.White else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.tid(Tid.CatalogManager.add)
            ) {
                Icon(Icons.Default.Add, "Add Catalog")
            }
        },
        containerColor = containerColor,
        contentColor = contentColor
    ) { padding ->
        if (catalogs.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No catalogs added yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Fix: explicit 'items' call resolving to OpdsEntity
                items(items = catalogs, key = { it.id }) { feed ->
                    CatalogManagerItem(
                        feed = feed,
                        isEink = isEink,
                        onEdit = {
                            editingCatalog = feed
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteCatalog(feed) },
                        modifier = if (catalogs.first() == feed) Modifier.tid(Tid.CatalogManager.first) else Modifier
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                }
            }
        }

        if (showDialog) {
            AddCatalogDialog(
                existingCatalog = editingCatalog,
                onDismiss = { showDialog = false },
                onSave = { title, url, user, pass, insecure ->
                    if (editingCatalog != null) {
                        viewModel.updateCatalog(editingCatalog!!, title, url, user, pass)
                    } else {
                        viewModel.addCatalog(title, url, user, pass)
                    }
                    showDialog = false
                }
            )
        }
    }
    }
}

@Composable
fun CatalogManagerItem(
    feed: OpdsEntity,
    isEink: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.tid(Tid.CatalogManager.item(feed.id.toString())),
        headlineContent = { Text(feed.title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(feed.url, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(
            containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface,
            headlineColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface
        ),
        trailingContent = {
            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.tid(Tid.CatalogManager.edit(feed.id.toString()))
                ) {
                    Icon(Icons.Default.Edit, "Edit Catalog", tint = Color.Gray)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.tid(Tid.CatalogManager.delete(feed.id.toString()))
                ) {
                    Icon(Icons.Default.Delete, "Delete Catalog", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun AddCatalogDialog(
    existingCatalog: OpdsEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existingCatalog?.title ?: "") }
    var url by remember { mutableStateOf(existingCatalog?.url ?: "") }
    var username by remember { mutableStateOf(existingCatalog?.username ?: "") }
    var password by remember { mutableStateOf(existingCatalog?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingCatalog == null) "Add Catalog" else "Edit Catalog") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tid(Tid.CatalogManager.dialogTitle)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (OPDS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tid(Tid.CatalogManager.dialogUrl)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tid(Tid.CatalogManager.dialogUser)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tid(Tid.CatalogManager.dialogPass)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, url, username, password, false) },
                enabled = title.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.tid(Tid.CatalogManager.dialogSave)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.tid(Tid.CatalogManager.dialogCancel)
            ) { Text("Cancel") }
        }
    )
}
