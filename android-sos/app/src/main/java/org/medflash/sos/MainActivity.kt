package org.medflash.sos

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.Toast
import java.util.UUID

/**
 * Experimental foreground-only BLE implementation. No background service or 20m guarantee.
 * The website remains unchanged. Do not publish as an update to the existing Play listing.
 */
class MainActivity : Activity() {
    private val serviceId = ParcelUuid(UUID.fromString("f97ca104-52b9-4b6d-95c3-3f3edb45a901"))
    private val prefs by lazy { getSharedPreferences("sos_solidaire", MODE_PRIVATE) }
    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private lateinit var web: WebView
    private var scanning = false
    private var advertising = false
    private var lastAlert = 0L
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.scanRecord?.serviceUuids?.contains(serviceId) != true) return
            val now = System.currentTimeMillis()
            if (now - lastAlert < 60000L) return
            lastAlert = now
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notification = android.app.Notification.Builder(this@MainActivity, "medflash_sos")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("MEDFLASH — SOS à proximité")
                .setContentText("Une personne proche a déclenché un SOS. Regardez autour de vous.")
                .setAutoCancel(true).build()
            manager.notify(2001, notification)
        }
    }
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            advertising = false
            Toast.makeText(this@MainActivity, "Diffusion SOS Bluetooth indisponible", Toast.LENGTH_LONG).show()
        }
    }
    private fun allowed() = prefs.getBoolean("consent", false)
    private fun permissions(): Array<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_SCAN
            p += Manifest.permission.BLUETOOTH_ADVERTISE
        } else p += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        return p.toTypedArray()
    }
    private fun granted() = permissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("medflash_sos", "Alertes SOS solidaires", NotificationManager.IMPORTANCE_HIGH))
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "tel") { startActivity(Intent(Intent.ACTION_DIAL, u)); return true }
                if (u.host != "hellxbone.github.io") {
                    startActivity(Intent(Intent.ACTION_VIEW, u)); return true
                }
                return false
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (!url.startsWith("https://hellxbone.github.io/MEDFLASH/")) return
                view.evaluateJavascript("""
                    (() => {
                      if(window.__medflashBLEHook) return;
                      window.__medflashBLEHook = true;
                      const screen = document.getElementById('screen');
                      if(!screen) return;
                      const sync = () => {
                        const active = !screen.hidden && screen.classList.contains('sos-image');
                        window.MedflashNative.sos(active);
                      };
                      new MutationObserver(sync).observe(screen,{attributes:true,attributeFilter:['hidden','class']});
                      sync();
                    })();
                """.trimIndent(), null)
            }
        }
        web.addJavascriptInterface(object {
            @JavascriptInterface fun sos(active: Boolean) {
                runOnUiThread { if (active) startAdvertise() else stopAdvertise() }
            }
        }, "MedflashNative")
        setContentView(web)
        web.loadUrl("https://hellxbone.github.io/MEDFLASH/")
        if (!prefs.contains("consent")) {
            AlertDialog.Builder(this).setTitle("MEDFLASH — SOS SOLIDAIRE")
                .setMessage("Autoriser les alertes SOS Bluetooth des personnes proches ? Gratuit, sans compte ni géolocalisation. La portée n'est pas garantie. Vous pouvez désactiver cette fonction dans les paramètres de l'application.")
                .setPositiveButton("J'autorise") { _, _ ->
                    prefs.edit().putBoolean("consent", true).apply()
                    requestPermissions(permissions(), 15)
                }
                .setNegativeButton("Non merci") { _, _ ->
                    prefs.edit().putBoolean("consent", false).apply()
                }.setCancelable(false).show()
        } else if (allowed() && !granted()) requestPermissions(permissions(), 15)
    }
    override fun onResume() { super.onResume(); startScan() }
    override fun onPause() { stopScan(); stopAdvertise(); super.onPause() }
    override fun onDestroy() { stopScan(); stopAdvertise(); web.destroy(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 15) startScan()
    }
    private fun startScan() {
        if (!allowed() || !granted() || adapter?.isEnabled != true || scanning) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        try {
            scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(serviceId).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            scanning = true
        } catch (_: SecurityException) { }
    }
    private fun stopScan() {
        if (!scanning) return
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: SecurityException) { }
        scanning = false
    }
    private fun startAdvertise() {
        if (!allowed() || !granted() || adapter?.isEnabled != true || advertising) return
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        try {
            advertiser.startAdvertising(
                AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW).setConnectable(false).build(),
                AdvertiseData.Builder().addServiceUuid(serviceId).setIncludeDeviceName(false).build(),
                advertiseCallback
            )
            advertising = true
        } catch (_: SecurityException) { }
    }
    private fun stopAdvertise() {
        if (!advertising) return
        try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: SecurityException) { }
        advertising = false
    }
}
