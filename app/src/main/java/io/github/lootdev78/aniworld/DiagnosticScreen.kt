package io.github.lootdev78.aniworld

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiagnosticScreen(
    entries: List<DiagnosticEntry>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exportText = entries.joinToString("\n\n") { it.asText() }
        .ifBlank { context.getString(R.string.diagnostic_empty) }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.diagnostic_title_count, entries.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        stringResource(R.string.diagnostics_screen_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(stringResource(R.string.diagnostics_logging), fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.diagnostics_logging_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = enabled, onCheckedChange = onEnabledChange)
                        }
                    }
                }

                if (!enabled) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                stringResource(R.string.diagnostics_disabled_hint),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (entries.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.diagnostic_empty),
                                modifier = Modifier.padding(18.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(entries, key = { "${it.timestamp}-${it.area}-${it.message.hashCode()}" }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(entry.area, fontWeight = FontWeight.Bold)
                                Text(entry.message)
                                if (entry.details.isNotBlank()) {
                                    HorizontalDivider()
                                    Text(
                                        entry.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    entry.asText().lineSequence().firstOrNull().orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                val copyAction: () -> Unit = {
                    runCatching {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostic_clip_label), exportText))
                    }.onFailure {
                        AppLogger.error(context.getString(R.string.diagnostics), context.getString(R.string.diagnostic_copy_error), it)
                    }
                }
                val shareAction: () -> Unit = {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostic_share_subject))
                                    putExtra(Intent.EXTRA_TEXT, exportText)
                                },
                                context.getString(R.string.diagnostic_share_chooser)
                            )
                        )
                    }.onFailure {
                        AppLogger.error(context.getString(R.string.diagnostics), context.getString(R.string.diagnostic_share_error), it)
                    }
                }
                when {
                    maxWidth < 330.dp -> {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticActionButton(
                                label = stringResource(R.string.copy),
                                icon = Icons.Default.ContentCopy,
                                onClick = copyAction,
                                compact = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DiagnosticActionButton(
                                label = stringResource(R.string.share),
                                icon = Icons.Default.Share,
                                onClick = shareAction,
                                compact = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DiagnosticActionButton(
                                label = stringResource(R.string.clear),
                                icon = Icons.Default.ClearAll,
                                onClick = onClear,
                                compact = false,
                                filled = true,
                                enabled = entries.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    maxWidth < 600.dp -> {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DiagnosticActionButton(
                                    label = stringResource(R.string.copy),
                                    icon = Icons.Default.ContentCopy,
                                    onClick = copyAction,
                                    compact = false,
                                    modifier = Modifier.weight(1f)
                                )
                                DiagnosticActionButton(
                                    label = stringResource(R.string.share),
                                    icon = Icons.Default.Share,
                                    onClick = shareAction,
                                    compact = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            DiagnosticActionButton(
                                label = stringResource(R.string.clear),
                                icon = Icons.Default.ClearAll,
                                onClick = onClear,
                                compact = false,
                                filled = true,
                                enabled = entries.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DiagnosticActionButton(
                                label = stringResource(R.string.copy),
                                icon = Icons.Default.ContentCopy,
                                onClick = copyAction,
                                compact = false,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticActionButton(
                                label = stringResource(R.string.share),
                                icon = Icons.Default.Share,
                                onClick = shareAction,
                                compact = false,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticActionButton(
                                label = stringResource(R.string.clear),
                                icon = Icons.Default.ClearAll,
                                onClick = onClear,
                                compact = false,
                                filled = true,
                                enabled = entries.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true
) {
    val buttonModifier = modifier.heightIn(min = 54.dp)
    val contentPadding = if (compact) {
        PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    } else {
        PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    }
    val content: @Composable () -> Unit = {
        if (compact) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, null, Modifier.size(22.dp))
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null)
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            contentPadding = contentPadding
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            contentPadding = contentPadding
        ) { content() }
    }
}

