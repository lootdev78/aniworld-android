package io.github.lootdev78.aniworld

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

internal const val SETTINGS_ACTIVITY_VIEWMODEL_ARG = "settings_activity"

enum class SettingsCategory(val titleRes: Int, val descriptionRes: Int, val icon: ImageVector) {
    PREFERRED(R.string.settings_category_preferred, R.string.settings_category_preferred_desc, Icons.Default.PlayCircle),
    APPEARANCE(R.string.settings_category_appearance, R.string.settings_category_appearance_desc, Icons.Default.Palette),
    METADATA(R.string.settings_category_metadata, R.string.settings_category_metadata_desc, Icons.Default.CloudDownload),
    BROWSER(R.string.settings_category_browser, R.string.settings_category_browser_desc, Icons.Default.Web),
    DIAGNOSTICS(R.string.settings_category_diagnostics, R.string.settings_category_diagnostics_desc, Icons.Default.BugReport),
    MAINTENANCE(R.string.settings_category_maintenance, R.string.settings_category_maintenance_desc, Icons.Default.Tune),
    CREDITS(R.string.settings_category_credits, R.string.settings_category_credits_desc, Icons.Default.Settings)
}

private fun settingsActivity(category: SettingsCategory): Class<out ComponentActivity> = when (category) {
    SettingsCategory.PREFERRED -> PlaybackSettingsActivity::class.java
    SettingsCategory.APPEARANCE -> AppearanceSettingsActivity::class.java
    SettingsCategory.METADATA -> MetadataSettingsActivity::class.java
    SettingsCategory.BROWSER -> BrowserSettingsActivity::class.java
    SettingsCategory.DIAGNOSTICS -> DiagnosticsSettingsActivity::class.java
    SettingsCategory.MAINTENANCE -> AppearanceSettingsActivity::class.java
    SettingsCategory.CREDITS -> CreditsSettingsActivity::class.java
}

fun openSettingsCategory(context: Context, category: SettingsCategory) {
    context.startActivity(Intent(context, settingsActivity(category)).putExtra(SETTINGS_ACTIVITY_VIEWMODEL_ARG, true))
}

fun openSettingsHub(context: Context) {
    context.startActivity(Intent(context, SettingsActivity::class.java).putExtra(SETTINGS_ACTIVITY_VIEWMODEL_ARG, true))
}

class SettingsActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            AniWorldTheme(state.preferences.useDynamicColors, state.preferences.accentColor, state.preferences.customAccentColor) {
                SettingsScreen(
                    st = state,
                    vm = vm,
                    category = null,
                    onOpenCategory = { openSettingsCategory(this, it) },
                    onDismiss = ::finish,
                    onDiagnostics = {
                        openSettingsCategory(this, SettingsCategory.DIAGNOSTICS)
                    }
                )
            }
        }
    }
}

abstract class SettingsCategoryActivity : ComponentActivity() {
    protected abstract val category: SettingsCategory
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            AniWorldTheme(state.preferences.useDynamicColors, state.preferences.accentColor, state.preferences.customAccentColor) {
                var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
                if (diagnosticsOpen) {
                    DiagnosticScreen(
                        entries = state.diagnostics,
                        enabled = state.preferences.diagnosticsEnabled,
                        onEnabledChange = vm::setDiagnosticsEnabled,
                        onClear = vm::clearDiagnostics,
                        onDismiss = { diagnosticsOpen = false }
                    )
                } else {
                    SettingsScreen(
                        st = state,
                        vm = vm,
                        category = category,
                        onOpenCategory = { openSettingsCategory(this, it) },
                        onDismiss = ::finish,
                        onDiagnostics = { diagnosticsOpen = true }
                    )
                }
            }
        }
    }
}

class PlaybackSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.PREFERRED
}

class AppearanceSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.APPEARANCE
}

class MetadataSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.METADATA
}

class BrowserSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.BROWSER
}

class DiagnosticsSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.DIAGNOSTICS
}

class MaintenanceSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.APPEARANCE
}

class CreditsSettingsActivity : SettingsCategoryActivity() {
    override val category = SettingsCategory.CREDITS
}
