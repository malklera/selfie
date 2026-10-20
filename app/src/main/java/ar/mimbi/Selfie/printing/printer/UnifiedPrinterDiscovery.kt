package ar.mimbi.Selfie.printing.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
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

class UnifiedPrinterDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _discoveredPrinters = MutableStateFlow<List<DiscoveredPrinter>>(emptyList())
    val discoveredPrinters: StateFlow<List<DiscoveredPrinter>> = _discoveredPrinters.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mdnsServiceTypes = listOf(
        "_ipp._tcp.",
        "_ipps._tcp.",
        "_printer._tcp.",
        "_pdl-datastream._tcp.",
        "_universal._sub._ipp._tcp."
    )

    fun startDiscovery() {
        if (_isSearching.value) return
        _isSearching.value = true
        _discoveredPrinters.value = emptyList()
        discoveryListeners.clear()

        // 1. USB Host Printers Search (Cable USB)
        searchUsbPrinters()

        // 2. Bluetooth Paired Printers Search (Bluetooth)
        searchBluetoothPrinters()

        // 3. mDNS / Network Discovery (Wi-Fi)
        startMdnsDiscovery()

        // 4. Subnet IP TCP Port Scan (Wi-Fi IP Scan)
        startSubnetScan()
    }

    private fun addDiscoveredPrinter(printer: DiscoveredPrinter) {
        synchronized(this) {
            val current = _discoveredPrinters.value.toMutableList()
            if (current.none { it.id == printer.id || (it.ipAddress != null && it.ipAddress == printer.ipAddress && it.connectionType == printer.connectionType) }) {
                current.add(printer)
                _discoveredPrinters.value = current
            }
        }
    }

    private fun searchUsbPrinters() {
        val manager = usbManager ?: return
        try {
            val deviceList = manager.deviceList
            deviceList.values.forEach { device ->
                var isPrinter = device.deviceClass == UsbConstants.USB_CLASS_PRINTER
                if (!isPrinter) {
                    for (i in 0 until device.interfaceCount) {
                        if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                            isPrinter = true
                            break
                        }
                    }
                }

                if (isPrinter) {
                    val vendorName = device.manufacturerName ?: "USB Vendor 0x${Integer.toHexString(device.vendorId)}"
                    val productName = device.productName ?: "Printer 0x${Integer.toHexString(device.productId)}"
                    val displayName = "$vendorName $productName".trim()

                    addDiscoveredPrinter(
                        DiscoveredPrinter(
                            id = "USB_${device.deviceId}",
                            name = if (displayName.isNotBlank()) displayName else "Impresora USB (${device.deviceName})",
                            connectionType = PrinterConnectionType.USB_HOST,
                            details = "Cable USB (VID: 0x${Integer.toHexString(device.vendorId).uppercase()}, PID: 0x${Integer.toHexString(device.productId).uppercase()})",
                            usbDevice = device
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun searchBluetoothPrinters() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!bluetoothAdapter.isEnabled) return

            val pairedDevices = bluetoothAdapter.bondedDevices ?: return
            pairedDevices.forEach { device ->
                val deviceClass = device.bluetoothClass?.majorDeviceClass
                val isImagingOrPrinter = deviceClass == BluetoothClass.Device.Major.IMAGING ||
                        deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED ||
                        deviceClass == BluetoothClass.Device.Major.MISC

                val name = device.name ?: "Impresora Bluetooth"
                val isNamePrinter = name.contains("print", ignoreCase = true) ||
                        name.contains("pos", ignoreCase = true) ||
                        name.contains("thermal", ignoreCase = true) ||
                        name.contains("epson", ignoreCase = true) ||
                        name.contains("zebra", ignoreCase = true) ||
                        name.contains("hp", ignoreCase = true)

                if (isImagingOrPrinter || isNamePrinter) {
                    addDiscoveredPrinter(
                        DiscoveredPrinter(
                            id = "BT_${device.address}",
                            name = name,
                            connectionType = PrinterConnectionType.BLUETOOTH,
                            details = "MAC Bluetooth: ${device.address}",
                            bluetoothAddress = device.address
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun startMdnsDiscovery() {
        val manager = nsdManager ?: return
        mdnsServiceTypes.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    try {
                        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                                val host = resolvedService.host
                                val ip = host?.hostAddress ?: return
                                val port = if (resolvedService.port > 0) resolvedService.port else 9100
                                val name = resolvedService.serviceName ?: "Impresora Wi-Fi"

                                addDiscoveredPrinter(
                                    DiscoveredPrinter(
                                        id = "MDNS_${ip}_$port",
                                        name = name,
                                        connectionType = PrinterConnectionType.WIFI_MDNS,
                                        details = "IP Wi-Fi: $ip:$port",
                                        ipAddress = ip,
                                        port = port
                                    )
                                )
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

    private fun startSubnetScan() {
        scanJob = scope.launch {
            try {
                val localIp = getLocalIpAddress()
                if (localIp != null) {
                    val subnetPrefix = localIp.substringBeforeLast(".") + "."
                    val commonPorts = listOf(9100, 631)

                    val jobs = mutableListOf<Job>()
                    for (i in 1..254) {
                        val ip = "$subnetPrefix$i"
                        if (ip == localIp) continue

                        jobs.add(launch(Dispatchers.IO) {
                            for (port in commonPorts) {
                                if (!isActive) break
                                try {
                                    Socket().use { socket ->
                                        socket.connect(InetSocketAddress(ip, port), 250)
                                        addDiscoveredPrinter(
                                            DiscoveredPrinter(
                                                id = "SUBNET_${ip}_$port",
                                                name = "Impresora de Red ($ip)",
                                                connectionType = PrinterConnectionType.WIFI_SUBNET,
                                                details = "IP Wi-Fi: $ip:$port",
                                                ipAddress = ip,
                                                port = port
                                            )
                                        )
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

    fun addManualWifiPrinter(name: String, ipAddress: String, port: Int = 9100) {
        val printer = DiscoveredPrinter(
            id = "MANUAL_${ipAddress}_$port",
            name = if (name.isNotBlank()) name else "Impresora Wi-Fi Manual",
            connectionType = PrinterConnectionType.WIFI_SUBNET,
            details = "IP Wi-Fi: $ipAddress:$port",
            ipAddress = ipAddress,
            port = port
        )
        addDiscoveredPrinter(printer)
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
