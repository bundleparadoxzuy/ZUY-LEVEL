package com.example

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class UserSession(val ip: String) {
    var mainClient: Socket? = null
    var mainServer: Socket? = null
    var extraClient: Socket? = null
    var extraServer: Socket? = null
    var startData: ByteArray? = null
    var totalGame = 0
    var isCancel = false
    var increase = true
    var definedLobby = false
    var isStart = false
    var cancelSearch = false
    var isHandlingStart = false
    
    val socketLock = Any()
    var matchJob: Job? = null
    var watchdogJob: Job? = null

    val deviceId: String
    init {
        val chars = "0123456789abcdef"
        val rnd = (1..16).map { chars[java.util.Random().nextInt(chars.length)] }.joinToString("")
        deviceId = "p$rnd"
    }

    fun sendToMainServer(data: ByteArray) {
        synchronized(socketLock) {
            try {
                val server = mainServer
                if (server != null && !server.isClosed && server.isConnected) {
                    server.outputStream.write(data)
                    server.outputStream.flush()
                }
            } catch (e: Exception) {
                // Ignore exception
            }
        }
    }
    
    fun close() {
        matchJob?.cancel()
        watchdogJob?.cancel()
        synchronized(socketLock) {
            try { mainClient?.close() } catch (e: Exception) {}
            try { mainServer?.close() } catch (e: Exception) {}
            try { extraClient?.close() } catch (e: Exception) {}
            try { extraServer?.close() } catch (e: Exception) {}
        }
    }
}

object SocksServerManager {
    private val socketScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var listenJob: Job? = null
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()
    
    private val _totalMatches = MutableStateFlow(0)
    val totalMatches = _totalMatches.asStateFlow()
    
    private val _runtimeSeconds = MutableStateFlow(0L)
    val runtimeSeconds = _runtimeSeconds.asStateFlow()
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    
    val sessions = ConcurrentHashMap<String, UserSession>()
    
    private var timerJob: Job? = null
    
    fun addLog(msg: String) {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formatted = "[$timeStr] $msg"
        val current = _logs.value.toMutableList()
        current.add(formatted)
        if (current.size > 250) current.removeAt(0)
        _logs.value = current
    }
    
    fun startServer(port: Int = 10221) {
        if (_isOnline.value) return
        
        _isOnline.value = true
        _runtimeSeconds.value = 0L
        _totalMatches.value = 0
        sessions.clear()
        
        addLog("SOCKS5 Server started on port $port")
        
        timerJob = socketScope.launch {
            while (isActive) {
                delay(1000)
                _runtimeSeconds.value += 1
            }
        }
        
        listenJob = socketScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port))
                }
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        try {
                            handleClient(clientSocket)
                        } catch (e: Exception) {
                            // ignore client exception
                        }
                    }
                }
            } catch (e: Exception) {
                addLog("Port $port binding error: ${e.message}")
                stopServer()
            }
        }
    }
    
    fun stopServer() {
        if (!_isOnline.value) return
        _isOnline.value = false
        addLog("SOCKS5 Server stopped")
        
        timerJob?.cancel()
        timerJob = null
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        
        listenJob?.cancel()
        listenJob = null
        
        for (session in sessions.values) {
            session.close()
        }
        sessions.clear()
    }
    
    private suspend fun handleClient(client: Socket) {
        val clientIp = client.inetAddress.hostAddress ?: "Unknown"
        val inputStream = client.inputStream
        val outputStream = client.outputStream
        
        try {
            val headerVersion = inputStream.read()
            if (headerVersion == -1) return
            val nMethods = inputStream.read()
            if (nMethods == -1) return
            
            if (headerVersion == 86 && nMethods == 114) {
                // Custom bypass protocol
            } else {
                val methods = ByteArray(nMethods)
                var bytesRead = 0
                while (bytesRead < nMethods) {
                    val r = inputStream.read(methods, bytesRead, nMethods - bytesRead)
                    if (r == -1) return
                    bytesRead += r
                }
                
                var hasUserPass = false
                for (b in methods) {
                    if (b.toInt() == 2) {
                        hasUserPass = true
                        break
                    }
                }
                
                if (!hasUserPass) {
                    client.close()
                    return
                }
                
                outputStream.write(byteArrayOf(5.toByte(), 2.toByte()))
                outputStream.flush()
                
                if (!authenticate(client)) {
                    return
                }
            }
            
            val reqVersion = inputStream.read()
            if (reqVersion == -1) return
            val cmd = inputStream.read()
            if (cmd == -1) return
            inputStream.read() // Skip RSVP
            val addressType = inputStream.read()
            if (addressType == -1) return
            
            var targetHost = ""
            if (addressType == 1) {
                val ipv4Bytes = ByteArray(4)
                inputStream.readFully(ipv4Bytes)
                targetHost = java.net.InetAddress.getByAddress(ipv4Bytes).hostAddress ?: ""
            } else if (addressType == 3) {
                val domainLen = inputStream.read()
                if (domainLen == -1) return
                val domainBytes = ByteArray(domainLen)
                inputStream.readFully(domainBytes)
                val domainName = String(domainBytes, Charsets.UTF_8)
                targetHost = withContext(Dispatchers.IO) {
                    try {
                        java.net.InetAddress.getByName(domainName).hostAddress ?: domainName
                    } catch (e: Exception) {
                        domainName
                    }
                }
            } else {
                client.close()
                return
            }
            
            val portByte1 = inputStream.read()
            val portByte2 = inputStream.read()
            if (portByte1 == -1 || portByte2 == -1) return
            val targetPort = ((portByte1 and 0xFF) shl 8) or (portByte2 and 0xFF)
            
            val remoteSocket = Socket()
            try {
                withContext(Dispatchers.IO) {
                    remoteSocket.connect(InetSocketAddress(targetHost, targetPort), 10000)
                }
            } catch (e: Exception) {
                val reply = byteArrayOf(5.toByte(), 5.toByte(), 0.toByte(), addressType.toByte(), 0, 0, 0, 0, 0, 0)
                outputStream.write(reply)
                outputStream.flush()
                client.close()
                return
            }
            
            val bindAddress = remoteSocket.localAddress.address
            val bindPort = remoteSocket.localPort
            val reply = ByteArray(10)
            reply[0] = 5.toByte()
            reply[1] = 0.toByte() // success
            reply[2] = 0.toByte()
            reply[3] = 1.toByte()
            System.arraycopy(bindAddress, 0, reply, 4, 4)
            reply[8] = ((bindPort shr 8) and 0xFF).toByte()
            reply[9] = (bindPort and 0xFF).toByte()
            outputStream.write(reply)
            outputStream.flush()
            
            relayData(client, remoteSocket, targetPort)
            
        } catch (e: Exception) {
            // Client error
        } finally {
            try { client.close() } catch (e: Exception) {}
        }
    }
    
    private fun authenticate(client: Socket): Boolean {
        val inputStream = client.inputStream
        val outputStream = client.outputStream
        try {
            val subVer = inputStream.read()
            if (subVer == -1) return false
            val userLen = inputStream.read()
            if (userLen == -1) return false
            val userBytes = ByteArray(userLen)
            var readLen = 0
            while (readLen < userLen) {
                val r = inputStream.read(userBytes, readLen, userLen - readLen)
                if (r == -1) return false
                readLen += r
            }
            val username = String(userBytes, Charsets.UTF_8)
            
            val passLen = inputStream.read()
            if (passLen == -1) return false
            val passBytes = ByteArray(passLen)
            readLen = 0
            while (readLen < passLen) {
                val r = inputStream.read(passBytes, readLen, passLen - readLen)
                if (r == -1) return false
                readLen += r
            }
            val password = String(passBytes, Charsets.UTF_8)
            
            if (username == "bot" && password == "bot") {
                outputStream.write(byteArrayOf(subVer.toByte(), 0.toByte()))
                outputStream.flush()
                return true
            } else {
                outputStream.write(byteArrayOf(subVer.toByte(), 0xFF.toByte()))
                outputStream.flush()
                client.close()
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }
    
    private suspend fun relayData(client: Socket, remote: Socket, targetPort: Int) {
        val clientIp = client.inetAddress.hostAddress ?: "Unknown"
        val session = sessions.getOrPut(clientIp) { UserSession(clientIp) }
        
        addLog("User Connected: $clientIp")
        
        coroutineScope {
            val jobA = launch(Dispatchers.IO) {
                val buffer = ByteArray(65535)
                val input = client.inputStream
                val output = remote.outputStream
                try {
                    while (isActive) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        if (bytesRead > 0) {
                            val data = ByteArray(bytesRead)
                            System.arraycopy(buffer, 0, data, 0, bytesRead)
                            
                            if (targetPort == 39699) {
                                synchronized(session.socketLock) {
                                    session.mainClient = client
                                    session.mainServer = remote
                                }
                            } else if (targetPort == 39800) {
                                synchronized(session.socketLock) {
                                    session.extraClient = client
                                    session.extraServer = remote
                                }
                            }
                            
                            val hexData = bytesToHex(data)
                            if (hexData.startsWith("0303") && data.size >= 400) {
                                session.startData = data
                                addLog("SOCKS5 [$clientIp] lobby search packet captured")
                            }
                            
                            output.write(data)
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    // Closed
                } finally {
                    try { remote.close() } catch (e: Exception) {}
                }
            }
            
            val jobB = launch(Dispatchers.IO) {
                val buffer = ByteArray(65535)
                val input = remote.inputStream
                val output = client.outputStream
                try {
                    while (isActive) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        if (bytesRead > 0) {
                            val data = ByteArray(bytesRead)
                            System.arraycopy(buffer, 0, data, 0, bytesRead)
                            
                            val hexData = bytesToHex(data)
                            if (hexData.startsWith("1200") && data.containsSubarray(byteArrayOf(0x6C.toByte(), 0x76.toByte()))) {
                                session.increase = true
                            }
                            
                            if (hexData.startsWith("0300") && data.size >= 50 && session.increase) {
                                if (!session.isHandlingStart) {
                                    session.isHandlingStart = true
                                    
                                    if (data.containsSubarray("Ranked Mode".toByteArray(Charsets.UTF_8))) {
                                        session.definedLobby = true
                                        session.totalGame = 0
                                        session.isHandlingStart = false
                                        addLog("SOCKS5 [$clientIp] Lobby mode: Ranked. Reset matches.")
                                    } else {
                                        session.isStart = true
                                        session.cancelSearch = true
                                        session.totalGame += 1
                                        
                                        _totalMatches.value = _totalMatches.value + 1
                                        addLog("Match Found! client: $clientIp | Game #${session.totalGame}")
                                        
                                        triggerTiepTucTimTran(session)
                                        session.isHandlingStart = false
                                    }
                                }
                            }
                            
                            output.write(data)
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    // Closed
                } finally {
                    try { client.close() } catch (e: Exception) {}
                }
            }
            
            joinAll(jobA, jobB)
            addLog("User Disconnected: $clientIp")
        }
    }
    
    private fun triggerTiepTucTimTran(session: UserSession) {
        session.matchJob?.cancel()
        session.matchJob = socketScope.launch {
            try {
                session.cancelSearch = false
                session.isStart = false
                
                triggerKiemTraThoiGianBatDau(session, session.totalGame)
                
                val stopDataHex = "030300000010d19a888c8587771d17487879bb627bec"
                val stopDataBytes = hexToBytes(stopDataHex)
                
                session.sendToMainServer(stopDataBytes)
                session.sendToMainServer(stopDataBytes)
                
                delay(4800)
                
                addLog("SOCKS5 [IP: ${session.ip}] Restarting lobby match search...")
                
                session.startData?.let {
                    session.sendToMainServer(it)
                }
                
                while (isActive) {
                    if (session.cancelSearch) {
                        break
                    }
                    delay(500)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun triggerKiemTraThoiGianBatDau(session: UserSession, currentMatch: Int) {
        session.watchdogJob?.cancel()
        session.watchdogJob = socketScope.launch {
            try {
                delay(15000)
                if (currentMatch == session.totalGame) {
                    session.cancelSearch = true
                    
                    val stopDataHex = "030300000010d19a888c8587771d17487879bb627bec"
                    val stopDataBytes = hexToBytes(stopDataHex)
                    session.sendToMainServer(stopDataBytes)
                    session.sendToMainServer(stopDataBytes)
                    
                    delay(2000)
                    if (currentMatch == session.totalGame) {
                        addLog("SOCKS5 [IP: ${session.ip}] stuck search. Re-triggering.")
                        triggerTiepTucTimTran(session)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val chars = "0123456789abcdef"
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = chars[v ushr 4]
            hexChars[j * 2 + 1] = chars[v and 0x0F]
        }
        return String(hexChars)
    }
    
    private fun ByteArray.containsSubarray(sub: ByteArray): Boolean {
        if (sub.isEmpty()) return true
        for (i in 0..this.size - sub.size) {
            var found = true
            for (j in sub.indices) {
                if (this[i + j] != sub[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }
    
    private fun java.io.InputStream.readFully(buffer: ByteArray) {
        var totalRead = 0
        val size = buffer.size
        while (totalRead < size) {
            val r = this.read(buffer, totalRead, size - totalRead)
            if (r == -1) throw java.io.IOException("Socket Stream EOF")
            totalRead += r
        }
    }
}
