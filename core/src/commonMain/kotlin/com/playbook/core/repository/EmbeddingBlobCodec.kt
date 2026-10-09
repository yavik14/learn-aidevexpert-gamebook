package com.playbook.core.repository

/**
 * Codec BLOB para vectores de embedding. Serializa `List<Float>` a bytes con
 * **big-endian explícito** usando `Float.toBits()`/`Float.fromBits()` y 4 bytes
 * por valor: es puro Kotlin common (sin dependencias) y estable entre
 * plataformas.
 *
 * `decode` exige una longitud múltiplo de 4; un BLOB truncado es corrupción y
 * debe fallar temprano (`IllegalArgumentException`).
 */
internal object EmbeddingBlobCodec {

    /** Serializa [values] a un `ByteArray` big-endian de `values.size * 4` bytes. */
    fun encode(values: List<Float>): ByteArray {
        val bytes = ByteArray(values.size * BYTES_PER_FLOAT)
        var offset = 0
        for (value in values) {
            val bits = value.toBits()
            bytes[offset] = (bits ushr 24).toByte()
            bytes[offset + 1] = (bits ushr 16).toByte()
            bytes[offset + 2] = (bits ushr 8).toByte()
            bytes[offset + 3] = bits.toByte()
            offset += BYTES_PER_FLOAT
        }
        return bytes
    }

    /** Decodifica un BLOB big-endian; exige longitud múltiplo de 4. */
    fun decode(bytes: ByteArray): List<Float> {
        require(bytes.size % BYTES_PER_FLOAT == 0) {
            "longitud de BLOB inválida: ${bytes.size} no es múltiplo de $BYTES_PER_FLOAT"
        }
        val values = ArrayList<Float>(bytes.size / BYTES_PER_FLOAT)
        var offset = 0
        while (offset < bytes.size) {
            val bits = ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            values.add(Float.fromBits(bits))
            offset += BYTES_PER_FLOAT
        }
        return values
    }

    private const val BYTES_PER_FLOAT = 4
}
