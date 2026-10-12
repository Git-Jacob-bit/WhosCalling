package io.github.gitjacobbit.whoscalling.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.gitjacobbit.whoscalling.ui.theme.Palette

data class PermissionStatus(
    val screeningRole: Boolean,
    val overlay: Boolean,
    val phoneState: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
) {
    /** Minimum needed for the overlay to show up during a call. */
    val essentialGranted: Boolean get() = screeningRole && overlay

    companion object {
        fun check(context: Context): PermissionStatus {
            val roles = context.getSystemService(RoleManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            return PermissionStatus(
                screeningRole = roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
                overlay = Settings.canDrawOverlays(context),
                phoneState = granted(Manifest.permission.READ_PHONE_STATE),
                notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
                batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun PermissionsChecklist(onStatus: (PermissionStatus) -> Unit = {}) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionStatus.check(context)) }
    fun refresh() {
        status = PermissionStatus.check(context)
        onStatus(status)
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val packageUri = Uri.parse("package:${context.packageName}")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionRow("Identyfikacja połączeń", "Pozwala sprawdzać nieznane numery i blokować spam", status.screeningRole) {
            val roles = context.getSystemService(RoleManager::class.java)
            activityLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
        PermissionRow("Wyświetlanie nad aplikacjami", "Potrzebne do pokazania nakładki podczas dzwonienia", status.overlay) {
            activityLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
        }
        PermissionRow("Stan połączenia i powiadomienia", "Chowa nakładkę po zakończeniu rozmowy", status.phoneState && status.notifications) {
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.READ_PHONE_STATE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            )
        }
        PermissionRow("Bez ograniczeń baterii", "Chroni przed ubijaniem aplikacji przez system (ważne na Xiaomi/Samsung)", status.batteryUnrestricted) {
            activityLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            null,
            tint = if (granted) Palette.Answer else Palette.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
        }
        if (!granted) {
            FilledTonalButton(onClick = onGrant, modifier = Modifier.padding(start = 8.dp)) { Text("Nadaj") }
        }
    }
}
