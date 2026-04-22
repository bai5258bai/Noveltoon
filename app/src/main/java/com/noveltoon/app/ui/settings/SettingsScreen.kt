package com.noveltoon.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noveltoon.app.BuildConfig
import com.noveltoon.app.R
import com.noveltoon.app.data.preferences.AppPreferences
import com.noveltoon.app.ui.sources.BuiltInSourceDialog
import com.noveltoon.app.ui.sources.BuiltInSourcePasswordDialog
import com.noveltoon.app.util.BackupManager
import com.noveltoon.app.util.CacheManager
import com.noveltoon.app.util.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToBookSources: () -> Unit = {},
    onNavigateToComicSources: () -> Unit = {}
) {
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
    var showBuiltInPasswordDialog by remember { mutableStateOf(false) }
    var showBuiltInDialog by remember { mutableStateOf(false) }
    var snackbarText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        cacheSize = CacheManager.formatSize(CacheManager.getCacheSize(context))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val ok = BackupManager.exportBackup(context, it)
                snackbarText = context.getString(if (ok) R.string.backup_success else R.string.backup_failed)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val ok = BackupManager.importBackup(context, it)
                snackbarText = context.getString(if (ok) R.string.restore_success else R.string.restore_failed)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) }
            )
        },
        snackbarHost = {
            if (snackbarText.isNotEmpty()) {
                Snackbar(
                    action = {
                        TextButton(onClick = { snackbarText = "" }) { Text(stringResource(R.string.ok)) }
                    }
                ) {
                    Text(snackbarText)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { SettingsSectionHeader(stringResource(R.string.settings_display)) }
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
            item { SettingsSectionHeader(stringResource(R.string.settings_sources)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_book_sources)) },
                    supportingContent = { Text(stringResource(R.string.manage_book_sources_desc)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                    modifier = Modifier.clickable { onNavigateToBookSources() }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_comic_sources)) },
                    supportingContent = { Text(stringResource(R.string.manage_comic_sources_desc)) },
                    leadingContent = { Icon(Icons.Default.Image, null) },
                    modifier = Modifier.clickable { onNavigateToComicSources() }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.builtin_source_manage)) },
                    supportingContent = { Text(stringResource(R.string.builtin_source_manage_desc)) },
                    leadingContent = { Icon(Icons.Default.Lock, null) },
                    modifier = Modifier.clickable { showBuiltInPasswordDialog = true }
                )
            }

            item { HorizontalDivider() }
            item { SettingsSectionHeader(stringResource(R.string.settings_network)) }
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
            item { SettingsSectionHeader(stringResource(R.string.settings_storage)) }
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
            item { SettingsSectionHeader(stringResource(R.string.settings_backup)) }
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
                            verticalAlignment = Alignment.CenterVertically
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
                        snackbarText = context.getString(R.string.cache_cleared)
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
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onSnackbar = { snackbarText = it }
        )
    }

    if (showBuiltInPasswordDialog) {
        BuiltInSourcePasswordDialog(
            onDismiss = { showBuiltInPasswordDialog = false },
            onSuccess = {
                showBuiltInPasswordDialog = false
                showBuiltInDialog = true
            }
        )
    }

    if (showBuiltInDialog) {
        BuiltInSourceDialog(onDismiss = { showBuiltInDialog = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, onSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.noveltoon.app.util.UpdateInfo?>(null) }
    var checkResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
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

                Spacer(Modifier.height(16.dp))
                if (checkResult != null) {
                    Text(
                        checkResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        checking = true
                        checkResult = null
                        scope.launch {
                            val info = UpdateChecker.fetchLatestRelease()
                            checking = false
                            if (info == null) {
                                checkResult = context.getString(R.string.update_failed)
                            } else if (UpdateChecker.isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                                updateInfo = info
                                checkResult = context.getString(R.string.update_available, info.versionName)
                            } else {
                                checkResult = context.getString(R.string.update_latest)
                            }
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.checking_update))
                    } else {
                        Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.check_update))
                    }
                }
                if (updateInfo != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.releaseUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.goto_download))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
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
