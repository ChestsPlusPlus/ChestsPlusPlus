import java.io.DataInputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal Source RCON client (Minecraft speaks the same protocol). Used to stop the E2E server. */
class Rcon(host: String, port: Int, password: String, timeoutMs: Int = 5_000) : AutoCloseable {
    private val socket = Socket()
    private var nextId = 1

    init {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = timeoutMs
        val id = send(TYPE_LOGIN, password)
        val response = read()
        check(response.id == id) { "RCON authentication failed" }
    }

    fun command(command: String): String {
        val id = send(TYPE_COMMAND, command)
        val response = read()
        check(response.id == id) { "Unexpected RCON response id ${response.id}" }
        return response.body
    }

    /** Sends a command without waiting for the reply (the server may close the connection, e.g. on `stop`). */
    fun fire(command: String) {
        send(TYPE_COMMAND, command)
    }

    private fun send(type: Int, body: String): Int {
        val id = nextId++
        val payload = body.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + 4 + 4 + payload.size + 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(4 + 4 + payload.size + 2).putInt(id).putInt(type).put(payload).put(0).put(0)
        socket.getOutputStream().apply {
            write(buffer.array())
            flush()
        }
        return id
    }

    private fun read(): Packet {
        val input = DataInputStream(socket.getInputStream())
        val length = Integer.reverseBytes(input.readInt())
        if (length < 10) throw EOFException("Malformed RCON packet")
        val data = ByteArray(length)
        input.readFully(data)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.int
        buffer.int // type
        return Packet(id, String(data, 8, length - 10, Charsets.UTF_8))
    }

    override fun close() = socket.close()

    private data class Packet(val id: Int, val body: String)

    private companion object {
        const val TYPE_COMMAND = 2
        const val TYPE_LOGIN = 3
    }
}
