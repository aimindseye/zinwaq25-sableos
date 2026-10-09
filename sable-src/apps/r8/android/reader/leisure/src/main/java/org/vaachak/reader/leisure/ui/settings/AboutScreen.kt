package org.vaachak.reader.leisure.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.vaachak.reader.leisure.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val versionLabel = buildString {
        append("Version ${BuildConfig.VERSION_NAME}")
        append(" (${BuildConfig.VERSION_CODE})")
    }

    val buildLabel = buildString {
        if (BuildConfig.SHOW_GIT_INFO) {
            append("Git ${BuildConfig.GIT_SHA}")
            if (BuildConfig.DEBUG) append(" • Debug")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Leisure Vaachak") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Leisure Vaachak",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (buildLabel.isNotBlank()) {
                        Text(
                            text = buildLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Part of Vaachak Platform",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "A privacy-first Android reading app built for distraction-free leisure reading on phones, tablets, and e-ink devices.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Leisure Vaachak separates leisure reading from study/work reading so users can stay immersed and undistracted.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Current experience includes EPUB reading, offline-first local library behavior, sync support, and Android TTS support including Hindi and Gujarati where available.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    AboutLinkRow(
                        title = "GitHub Repository",
                        subtitle = "Source code for Leisure Vaachak",
                        onClick = {
                            uriHandler.openUri("https://github.com/vaachak-platform/vaachak-mobile")
                        }
                    )

                    AboutLinkRow(
                        title = "Vaachak Platform",
                        subtitle = "Organization and platform overview",
                        onClick = {
                            uriHandler.openUri("https://github.com/vaachak-platform")
                        }
                    )

                    AboutLinkRow(
                        title = "License",
                        subtitle = "MIT License",
                        onClick = {
                            uriHandler.openUri("https://github.com/vaachak-platform/vaachak-mobile/blob/main/LICENSE")
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Technology",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Built with Kotlin, Jetpack Compose, Room, Readium, Hilt, and a Kotlin Multiplatform-ready core.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Sync is designed around privacy-preserving principles so reading progress, bookmarks, and highlights can be synchronized without turning user data into a product.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Open Source Acknowledgements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    LicenseItem(
                        library = "Leisure Vaachak",
                        license = "MIT License"
                    )
                    HorizontalDivider()
                    LicenseItem(
                        library = "Readium Kotlin Toolkit",
                        license = "BSD 3-Clause License"
                    )
                    HorizontalDivider()
                    LicenseItem(
                        library = "Android Jetpack / Material 3 / Room / Hilt",
                        license = "Apache License 2.0"
                    )
                    HorizontalDivider()
                    LicenseItem(
                        library = "Retrofit / OkHttp / Coil",
                        license = "Apache License 2.0"
                    )
                    HorizontalDivider()
                    LicenseItem(
                        library = "Google Generative AI",
                        license = "Apache License 2.0"
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = "© 2026 Piyush Daiya",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Copyright 2026 Piyush Daiya"
                        }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = null
            )
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LicenseItem(
    library: String,
    license: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "License for $library: $license"
            }
    ) {
        Text(
            text = library,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = license,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}