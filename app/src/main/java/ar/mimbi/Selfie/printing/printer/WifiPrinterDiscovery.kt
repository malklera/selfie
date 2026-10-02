package ar.mimbi.Selfie.printing.printer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class WifiPrinterDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _discoveredPrinters = MutableStateFlow<List<WifiPrinterInfo>>(emptyList())
    val discoveredPrinters: StateFlow<List<WifiPrinterInfo>> = _discoveredPrinters.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val serviceTypes = listOf(
        "_ipp._tcp.",
        "_ipps._tcp.",
        "_printer._tcp.",
        "_pdl-datastream._tcp."
    )

    fun startDiscovery() {
        if (_isSearching.value) return
        _isSearching.value = true
        _discoveredPrinters.value = emptyList()
        discoveryListeners.clear()

        // 1. Start mDNS discovery for multiple service types
        nsdManager?.let { manager ->
            serviceTypes.forEach { type ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        try {
                            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                                    val host = resolvedService.host
                                    val ip = host?.hostAddress ?: return
                                    val port = resolvedService.port
                                    val name = resolvedService.serviceName ?: "Impresora Wi-Fi"

                                    val printerInfo = WifiPrinterInfo(
                                        name = name,
                                        ipAddress = ip,
                                        port = if (port > 0) port else 9100,
                                        serviceType = resolvedService.serviceType ?: ""
                                    )

                                    addDiscoveredPrinter(printerInfo)
                                }

                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            })
                        } catch (_: Exception) {}
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
                discoveryListeners.add(listener)
                try {
                    manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (_: Exception) {}
            }
        }

        // 2. Start Subnet IP Scan (to find printers where mDNS is blocked or disabled)
        scanJob = scope.launch {
            try {
                val localIp = getLocalIpAddress()
                if (localIp != null) {
                    val subnetPrefix = localIp.substringBeforeLast(".") + "."
                    val commonPorts = listOf(9100, 631) // Raw and IPP ports

                    val jobs = mutableListOf<Job>()
                    for (i in 1..254) {
                        val ip = "$subnetPrefix$i"
                        if (ip == localIp) continue // Skip own device IP

                        jobs.add(launch(Dispatchers.IO) {
                            for (port in commonPorts) {
                                if (!isActive) break
                                try {
                                    Socket().use { socket ->
                                        socket.connect(InetSocketAddress(ip, port), 250) // 250ms timeout per IP/port
                                        val printerInfo = WifiPrinterInfo(
                                            name = "Impresora de Red ($ip)",
                                            ipAddress = ip,
                                            port = port,
                                            serviceType = "Subnet Scan"
                                        )
                                        addDiscoveredPrinter(printerInfo)
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        })
                    }
                    jobs.joinAll()
                }
            } catch (_: Exception) {} finally {
                delay(1000.milliseconds)
                _isSearching.value = false
            }
        }
    }

    private fun addDiscoveredPrinter(printerInfo: WifiPrinterInfo) {
        val current = _discoveredPrinters.value.toMutableList()
        if (current.none { it.ipAddress == printerInfo.ipAddress }) {
            current.add(printerInfo)
            _discoveredPrinters.value = current
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        nsdManager?.let { manager ->
            discoveryListeners.forEach { listener ->
                try {
                    manager.stopServiceDiscovery(listener)
                } catch (_: Exception) {}
            }
        }
        discoveryListeners.clear()
        _isSearching.value = false
    }

    fun addManualPrinter(name: String, ipAddress: String, port: Int = 9100) {
        val printerInfo = WifiPrinterInfo(name = name, ipAddress = ipAddress, port = port)
        addDiscoveredPrinter(printerInfo)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
