package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var scanButton: Button
    private lateinit var macAddressTextView: TextView

    private val REQUEST_BLUETOOTH_PERMISSIONS = 1
    private val CHECK_INTERVAL: Long = 500 // 檢查間隔時間（毫秒）
    private val DATA_TIMEOUT: Long = 1000 // 数据超时时间（毫秒）

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readThread: Thread? = null

    private lateinit var luxTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var humidityTextView: TextView
    private lateinit var reconnectButton: Button
    private lateinit var startStopButton: Button
    private lateinit var connectionStatusTextView: TextView
    private lateinit var statusIndicator: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var lastDataReceivedTime: Long = 0

    private lateinit var recyclerView: RecyclerView
    private lateinit var macAddressAdapter: MacAddressAdapter

    private var DEVICE_ADDRESS = "" // 替換為 ESP32 的藍牙地址
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        macAddressTextView = findViewById(R.id.macAddressTextView)
        scanButton = findViewById(R.id.scanButton)
        luxTextView = findViewById(R.id.luxTextView)
        temperatureTextView = findViewById(R.id.temperatureTextView)
        humidityTextView = findViewById(R.id.humidityTextView)
        reconnectButton = findViewById(R.id.reconnectButton)
        startStopButton = findViewById(R.id.startStopButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        statusIndicator = findViewById(R.id.statusIndicator)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        macAddressAdapter = MacAddressAdapter(mutableListOf(), this::deleteMacAddress, this::renameMacAddress, this::connectToMacAddress)
        recyclerView.adapter = macAddressAdapter

        reconnectButton.setOnClickListener {
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                disconnectBluetooth()
            } else {
                setupBluetoothConnection()
            }
        }

        startStopButton.setOnClickListener {
            if (startStopButton.text == getString(R.string.start)) {
                sendStartCommand()
                startStopButton.text = getString(R.string.stop)
            } else {
                sendStopCommand()
                startStopButton.text = getString(R.string.start)
            }
        }

        scanButton.setOnClickListener {
            IntentIntegrator(this).initiateScan()
        }

        if (checkPermissions()) {
            setupBluetoothConnection()
        } else {
            requestPermissions()
        }

        handler.post(checkConnectionStatusRunnable)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                macAddressTextView.text = getString(R.string.cancelled)
            } else {
                macAddressTextView.text = "ESP32 MAC Address: " + result.contents
                DEVICE_ADDRESS = result.contents
                disconnectBluetooth() // Disconnect current connection if any
                setupBluetoothConnection() // Connect to the new device
                if (!macAddressAdapter.items.any { it.macAddress == result.contents }) {
                    val newItem = MacAddressItem(result.contents, "ESP32-${macAddressAdapter.itemCount + 1}")
                    macAddressAdapter.addItem(newItem)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private val checkConnectionStatusRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            if (bluetoothSocket != null && bluetoothSocket!!.isConnected && isBluetoothDeviceConnected()) {
                if (currentTime - lastDataReceivedTime > DATA_TIMEOUT) {
                    runOnUiThread {
                        luxTextView.text = getString(R.string.lux_default)
                        temperatureTextView.text = getString(R.string.temperature_default)
                        humidityTextView.text = getString(R.string.humidity_default)
                        connectionStatusTextView.text = getString(R.string.status_not_connected)
                        statusIndicator.setImageResource(R.drawable.not_connected)
                        reconnectButton.text = getString(R.string.connect)
                    }
                } else {
                    runOnUiThread {
                        connectionStatusTextView.text = getString(R.string.status_connected)
                        statusIndicator.setImageResource(R.drawable.connected)
                        reconnectButton.text = getString(R.string.disconnect)
                    }
                }
            } else {
                runOnUiThread {
                    connectionStatusTextView.text = getString(R.string.status_not_connected)
                    statusIndicator.setImageResource(R.drawable.not_connected)
                    reconnectButton.text = getString(R.string.connect)
                }
            }
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    private fun isBluetoothDeviceConnected(): Boolean {
        return try {
            bluetoothSocket?.outputStream?.write("".toByteArray())
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
    }

    private fun setupBluetoothConnection() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        if (DEVICE_ADDRESS.isEmpty()) {
            runOnUiThread {
                connectionStatusTextView.text = getString(R.string.status_not_connected)
                statusIndicator.setImageResource(R.drawable.not_connected)
            }
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS)

        readThread?.interrupt()
        readThread = null

        Thread {
            runOnUiThread {
                reconnectButton.isEnabled = false
                connectionStatusTextView.text = getString(R.string.status_connecting)
                statusIndicator.setImageResource(R.drawable.connecting)
            }
            try {
                bluetoothSocket?.close()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                runOnUiThread {
                    reconnectButton.isEnabled = true
                    connectionStatusTextView.text = getString(R.string.status_connected)
                    statusIndicator.setImageResource(R.drawable.connected)
                    reconnectButton.text = getString(R.string.disconnect)
                }
                startReadingData()
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    reconnectButton.isEnabled = true
                    connectionStatusTextView.text = getString(R.string.status_not_connected)
                    statusIndicator.setImageResource(R.drawable.not_connected)
                    reconnectButton.text = getString(R.string.connect)
                    luxTextView.text = getString(R.string.lux_default)
                    temperatureTextView.text = getString(R.string.temperature_default)
                    humidityTextView.text = getString(R.string.humidity_default)
                }
            }
        }.start()
    }

    private fun startReadingData() {
        readThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val bytesAvailable = inputStream?.available() ?: 0
                    if (bytesAvailable > 0) {
                        val buffer = ByteArray(1024)
                        val bytesRead = inputStream?.read(buffer) ?: 0
                        val data = String(buffer, 0, bytesRead)
                        lastDataReceivedTime = System.currentTimeMillis()
                        runOnUiThread {
                            parseAndDisplayData(data)
                        }
                    } else {
                        if (!isBluetoothDeviceConnected()) {
                            runOnUiThread {
                                luxTextView.text = getString(R.string.lux_default)
                                temperatureTextView.text = getString(R.string.temperature_default)
                                humidityTextView.text = getString(R.string.humidity_default)
                                connectionStatusTextView.text = getString(R.string.status_not_connected)
                                statusIndicator.setImageResource(R.drawable.not_connected)
                                reconnectButton.text = getString(R.string.connect)
                            }
                            break
                        }
                    }
                    Thread.sleep(500)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    luxTextView.text = getString(R.string.lux_default)
                    temperatureTextView.text = getString(R.string.temperature_default)
                    humidityTextView.text = getString(R.string.humidity_default)
                    connectionStatusTextView.text = getString(R.string.status_not_connected)
                    statusIndicator.setImageResource(R.drawable.not_connected)
                    reconnectButton.text = getString(R.string.connect)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                try {
                    inputStream?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                try {
                    bluetoothSocket?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        readThread?.start()
    }

    private fun sendStartCommand() {
        try {
            outputStream?.write("start\n".toByteArray())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sendStopCommand() {
        try {
            outputStream?.write("stop\n".toByteArray())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun deleteMacAddress(macAddressItem: MacAddressItem) {
        macAddressAdapter.removeItem(macAddressItem)
        if (macAddressAdapter.itemCount == 0) {
            DEVICE_ADDRESS = ""
            disconnectBluetooth()
        }
    }

    private fun renameMacAddress(macAddressItem: MacAddressItem, newName: String) {
        macAddressAdapter.renameItem(macAddressItem, newName)
    }

    private fun connectToMacAddress(macAddressItem: MacAddressItem) {
        DEVICE_ADDRESS = macAddressItem.macAddress
        setupBluetoothConnection()
    }

    private fun disconnectBluetooth() {
        try {
            readThread?.interrupt()
            inputStream?.close()
            bluetoothSocket?.close()
            bluetoothSocket = null
            runOnUiThread {
                connectionStatusTextView.text = getString(R.string.status_not_connected)
                statusIndicator.setImageResource(R.drawable.not_connected)
                reconnectButton.text = getString(R.string.connect)
                startStopButton.text = getString(R.string.start)
                luxTextView.text = getString(R.string.lux_default)
                temperatureTextView.text = getString(R.string.temperature_default)
                humidityTextView.text = getString(R.string.humidity_default)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun parseAndDisplayData(data: String) {
        val values = data.split(",")
        if (values.size == 3) {
            val luxValue = values[0].split(":").getOrNull(1)?.trim()
            val temperatureValue = values[1].split(":").getOrNull(1)?.trim()
            val humidityValue = values[2].split(":").getOrNull(1)?.trim()

            if (luxValue != null && temperatureValue != null && humidityValue != null) {
                luxTextView.text = "Lux: $luxValue"
                temperatureTextView.text = "Temperature: $temperatureValue"
                humidityTextView.text = "Humidity: $humidityValue"
            } else {
                logError("Invalid data format: $data")
            }
        } else {
            logError("Unexpected data format: $data")
        }
    }

    private fun logError(errorMessage: String) {
        println(errorMessage)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if ((grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })) {
                setupBluetoothConnection()
            } else {
                // 權限被拒絕，顯示信息給用戶
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkConnectionStatusRunnable)
        try {
            readThread?.interrupt()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }
}
