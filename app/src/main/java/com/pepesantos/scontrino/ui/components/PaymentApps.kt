package com.pepesantos.scontrino.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.nfc.cardemulation.OffHostApduService
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pepesantos.scontrino.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** An installed app that can be opened from the wallet, with its own icon and label. */
class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap)

/**
 * Apps offered as the wallet's payment shortcut: [payment] are the ones registered for NFC
 * contactless payments (the system default first) plus known payment apps, [others] every
 * other launcher app (bank apps, Bizum through the bank...).
 */
class PaymentAppChoices(
    val payment: List<LaunchableApp>,
    val others: List<LaunchableApp>,
    val defaultPackage: String?,
)

/**
 * The payment app shortcut of the wallet: no Google library, only the package manager.
 * Third party logos are never bundled: the installed app's own icon is shown at runtime.
 */
object PaymentApps {

    private const val ICON_SIZE_PX = 96

    /** Debug builds only for now. */
    fun isEnabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** The app, or null if it was uninstalled or can no longer be opened. */
    fun load(context: Context, packageName: String): LaunchableApp? {
        if (packageName.isEmpty()) return null
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(packageName) == null) return null
        return runCatching { launchable(pm, packageName) }.getOrNull()
    }

    /** Opens the app; false if it can't be opened any more. */
    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Slow (icons of every launcher app): call off the main thread. */
    fun loadChoices(context: Context): PaymentAppChoices {
        val pm = context.packageManager
        val own = context.packageName
        val services = nfcPaymentServices(pm)
        val defaultPackage = defaultPaymentPackage(context, services)?.let { frontApp(pm, it) }

        val paymentPackages = (
            services.mapNotNull { frontApp(pm, it.packageName) } +
                KNOWN_PAYMENT_APPS.filter { pm.getLaunchIntentForPackage(it) != null }
            ).distinct().filter { it != own }
        @Suppress("DEPRECATION")
        val launcherPackages = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.distinct()
            .filter { it != own && it !in paymentPackages }

        fun build(packages: List<String>) =
            packages.mapNotNull { runCatching { launchable(pm, it) }.getOrNull() }
                .sortedBy { it.label.lowercase() }

        val payment = build(paymentPackages).sortedByDescending { it.packageName == defaultPackage }
        return PaymentAppChoices(payment, build(launcherPackages), defaultPackage)
    }

    /**
     * Some payment services live in a package without a launcher icon: Google Wallet's tap to
     * pay runs inside Google Play services. The user opens the app in front of it instead.
     */
    private val FRONT_APPS = mapOf(
        "com.google.android.gms" to listOf("com.google.android.apps.walletnfcrel"),
    )

    /**
     * Payment apps without an NFC payment service of their own (they pay through Google Wallet,
     * by QR or by transfer), so the system can't tell they're payment apps. Hand-kept: add new
     * ones here. Bizum has no app of its own (it lives inside each Spanish bank's app).
     */
    private val KNOWN_PAYMENT_APPS = listOf(
        "com.revolut.revolut",          // Revolut
        "com.paypal.android.p2pmobile", // PayPal
        "com.satispay.customer",        // Satispay
        "com.imaginecurve.curve.prd",   // Curve
        "com.transferwise.android",     // Wise
        "de.number26.android",          // N26
        "com.bunq.android",             // bunq
        "pt.sibs.android.mbway",        // MB WAY
        "com.eg.android.AlipayGphone",  // Alipay
        "com.yellowpepper.pichincha",   // Banco Pichincha (Ecuador)
        "com.bancodeguayaquil",         // Banco Guayaquil (Ecuador)
    )

    /** The app to open for a payment service's [packageName], or null if there's none. */
    private fun frontApp(pm: PackageManager, packageName: String): String? =
        if (pm.getLaunchIntentForPackage(packageName) != null) {
            packageName
        } else {
            FRONT_APPS[packageName]?.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
        }

    private fun launchable(pm: PackageManager, packageName: String): LaunchableApp {
        val info = pm.getApplicationInfo(packageName, 0)
        return LaunchableApp(
            packageName = packageName,
            label = pm.getApplicationLabel(info).toString(),
            icon = pm.getApplicationIcon(info).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap(),
        )
    }

    /** Card emulation services with an aid-group of category "payment" (host or secure element). */
    private fun nfcPaymentServices(pm: PackageManager): List<ServiceInfo> =
        listOf(
            HostApduService.SERVICE_INTERFACE to HostApduService.SERVICE_META_DATA,
            OffHostApduService.SERVICE_INTERFACE to OffHostApduService.SERVICE_META_DATA,
        ).flatMap { (action, metaData) ->
            @Suppress("DEPRECATION")
            pm.queryIntentServices(Intent(action), PackageManager.GET_META_DATA)
                .mapNotNull { it.serviceInfo }
                .filter { hasPaymentAids(pm, it, metaData) }
        }

    private fun hasPaymentAids(pm: PackageManager, service: ServiceInfo, metaData: String): Boolean {
        val parser = runCatching { service.loadXmlMetaData(pm, metaData) }.getOrNull() ?: return false
        return try {
            var event = parser.next()
            var found = false
            while (event != XmlPullParser.END_DOCUMENT && !found) {
                found = event == XmlPullParser.START_TAG && parser.name == "aid-group" &&
                    (0 until parser.attributeCount).any {
                        parser.getAttributeName(it) == "category" &&
                            parser.getAttributeValue(it) == CardEmulation.CATEGORY_PAYMENT
                    }
                event = parser.next()
            }
            found
        } catch (_: Exception) {
            false
        } finally {
            parser.close()
        }
    }

    /** Package of the system's default contactless payment app, if it can be read. */
    private fun defaultPaymentPackage(context: Context, services: List<ServiceInfo>): String? =
        runCatching {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
                return@runCatching null
            }
            val adapter = NfcAdapter.getDefaultAdapter(context) ?: return@runCatching null
            val cardEmulation = CardEmulation.getInstance(adapter)
            services.firstOrNull {
                cardEmulation.isDefaultServiceForCategory(
                    ComponentName(it.packageName, it.name), CardEmulation.CATEGORY_PAYMENT
                )
            }?.packageName
        }.getOrNull()
}

/**
 * Picks the wallet's payment app: "none", the contactless payment apps and then every
 * other app. Applied on tap; [onSelect] gets an empty string for "none".
 */
@Composable
fun PaymentAppDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val choices by produceState<PaymentAppChoices?>(null) {
        value = withContext(Dispatchers.IO) { PaymentApps.loadChoices(context.applicationContext) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_payment_app)) },
        text = {
            val loaded = choices
            if (loaded == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    item {
                        AppRow(
                            label = stringResource(R.string.settings_payment_app_none),
                            icon = null,
                            selected = current.isEmpty(),
                            onClick = { onSelect("") },
                        )
                    }
                    if (loaded.payment.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.payment_app_section_payment)) }
                        items(loaded.payment, key = { "p:" + it.packageName }) { app ->
                            AppRow(
                                label = app.label,
                                icon = app.icon,
                                supporting = if (app.packageName == loaded.defaultPackage) {
                                    stringResource(R.string.payment_app_system_default)
                                } else null,
                                selected = app.packageName == current,
                                onClick = { onSelect(app.packageName) },
                            )
                        }
                    }
                    if (loaded.others.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.payment_app_section_other)) }
                        items(loaded.others, key = { "o:" + it.packageName }) { app ->
                            AppRow(
                                label = app.label,
                                icon = app.icon,
                                selected = app.packageName == current,
                                onClick = { onSelect(app.packageName) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_cancel)) }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(
    label: String,
    icon: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            } else {
                Box(Modifier.size(32.dp))
            }
        },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    )
}
