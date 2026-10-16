package io.github.cosmichut

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 设备管理员：提供卸载保护（激活后需先在 App 内停用才能卸载）。
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        val l10n = LockManager.localized(context)
        Toast.makeText(context, l10n.getString(R.string.admin_enabled_toast), Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val l10n = LockManager.localized(context)
        Toast.makeText(context, l10n.getString(R.string.admin_disabled_toast), Toast.LENGTH_LONG).show()
    }
}
