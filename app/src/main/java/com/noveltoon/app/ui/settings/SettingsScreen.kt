package com.noveltoon.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noveltoon.app.R
import com.noveltoon.app.data.preferences.AppPreferences
import com.noveltoon.app.util.BackupManager
import com.noveltoon.app.util.CacheManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    val themeMode by prefs.themeMode.collectAsState(initial = 2)
    val wifiOnly by prefs.wifiOnlyOriginal.collectAsState(initial = false)
    val autoClearDays by prefs.autoClearCacheDays.collectAsState(initial = 7)

    var cacheSize by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        cacheSize = CacheManager.formatSize(CacheManager.getCacheSize(context))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = BackupManager.exportBackup(context, it)
                showSnackbar = if (success) context.getString(R.string.backup_success)
                else context.getString(R.string.backup_failed)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = BackupManager.importBackup(context, it)
                showSnackbar = if (success) context.getString(R.string.restore_success)
                else context.getString(R.string.restore_failed)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                windowInsets = WindowInsets(0)
            )
        },
        snackbarHost = {
            if (showSnackbar.isNotEmpty()) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showSnackbar = "" }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                ) {
                    Text(showSnackbar)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsSectionHeader(stringResource(R.string.settings_display))
            }
            item {
                val themeText = when (themeMode) {
                    0 -> stringResource(R.string.theme_light)
                    1 -> stringResource(R.string.theme_dark)
                    else -> stringResource(R.string.theme_system)
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_mode)) },
                    supportingContent = { Text(themeText) },
                    leadingContent = { Icon(Icons.Default.Palette, null) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_network))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wifi_only_original)) },
                    supportingContent = { Text(stringResource(R.string.wifi_only_desc)) },
                    leadingContent = { Icon(Icons.Default.Wifi, null) },
                    trailingContent = {
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { scope.launch { prefs.setWifiOnlyOriginal(it) } }
                        )
                    }
                )
            }

            item { HorizontalDivider() }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_storage))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.image_cache)) },
                    supportingContent = { Text(cacheSize) },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    modifier = Modifier.clickable { showClearCacheDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.auto_clear_cache)) },
                    supportingContent = {
                        Text(
                            if (autoClearDays > 0) stringResource(R.string.auto_clear_days, autoClearDays)
                            else stringResource(R.string.auto_clear_off)
                        )
                    },
                    leadingContent = { Icon(Icons.Default.CleaningServices, null) },
                    modifier = Modifier.clickable {
                        val next = when (autoClearDays) {
                            0 -> 3
                            3 -> 7
                            7 -> 14
                            14 -> 30
                            else -> 0
                        }
                        scope.launch { prefs.setAutoClearCacheDays(next) }
                    }
                )
            }

            item { HorizontalDivider() }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_backup))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_backup)) },
                    supportingContent = { Text(stringResource(R.string.export_desc)) },
                    leadingContent = { Icon(Icons.Default.Upload, null) },
                    modifier = Modifier.clickable {
                        exportLauncher.launch("noveltoon_backup.json")
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.import_backup)) },
                    supportingContent = { Text(stringResource(R.string.import_desc)) },
                    leadingContent = { Icon(Icons.Default.Download, null) },
                    modifier = Modifier.clickable {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
            }

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about)) },
                    supportingContent = { Text(stringResource(R.string.about_personal_use)) },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.clickable { showAboutDialog = true }
                )
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme_mode)) },
            text = {
                Column {
                    listOf(
                        0 to stringResource(R.string.theme_light),
                        1 to stringResource(R.string.theme_dark),
                        2 to stringResource(R.string.theme_system)
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefs.setThemeMode(value) }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = {
                                    scope.launch { prefs.setThemeMode(value) }
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.clear_cache)) },
            text = { Text(stringResource(R.string.clear_cache_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        CacheManager.clearCache(context)
                        cacheSize = CacheManager.formatSize(CacheManager.getCacheSize(context))
                        showClearCacheDialog = false
                        showSnackbar = context.getString(R.string.cache_cleared)
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_version, "1.0.1"))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.about_personal_use))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.about_developer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.about_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
