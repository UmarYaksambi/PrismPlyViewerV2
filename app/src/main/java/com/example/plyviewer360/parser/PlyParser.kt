package com.example.plyviewer360.parser

import android.util.Log
import com.example.plyviewer360.data.AABB
import com.example.plyviewer360.data.SplatPointCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object PlyParser {
    private const val TAG = "PlyParser"
    // Spherical Harmonic Constant (0th degree)
    private const val SH_C0 = 0.28209479177387814f

    suspend fun parse(inputStream: InputStream): SplatPointCloud = withContext(Dispatchers.IO) {
        val reader = inputStream.buffered()

        // --- 1. Header Parsing ---
        var line: String?
        var vertexCount = 0
        var isBinary = false
        val properties = ArrayList<String>()

        while (true) {
            line = readLine(reader)
            if (line == null || line == "end_header") break

            val tokens = line.split("\\s+".toRegex())
            if (tokens[0] == "format" && tokens[1] == "binary_little_endian") isBinary = true
            if (tokens[0] == "element" && tokens[1] == "vertex") vertexCount = tokens[2].toInt()
            if (tokens[0] == "property") properties.add(tokens[2])
        }

        // --- 2. Allocation (Off-Heap Memory) ---
        // 3 floats for Pos, 4 floats for Color (RGBA)
        val posBuffer = ByteBuffer.allocateDirect(vertexCount * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val colorBuffer = ByteBuffer.allocateDirect(vertexCount * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val bounds = AABB()

        // --- 3. Property Mapping ---
        val stride = properties.size * 4 // Assuming 4 bytes per float
        val chunk = ByteArray(stride)
        val wrapper = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)

        val idxX = properties.indexOf("x")
        val idxY = properties.indexOf("y")
        val idxZ = properties.indexOf("z")
        // Gaussian Splatting standard names for DC coefficients
        val idxR = maxOf(properties.indexOf("f_dc_0"), properties.indexOf("red"))
        val idxG = maxOf(properties.indexOf("f_dc_1"), properties.indexOf("green"))
        val idxB = maxOf(properties.indexOf("f_dc_2"), properties.indexOf("blue"))
        val idxOp = properties.indexOf("opacity")

        // --- 4. Fast Binary Read ---
        for (i in 0 until vertexCount) {
            // CRITICAL FIX: Read fully
            // reader.read(chunk) might not read all 'stride' bytes at once if buffer is fragmented
            var bytesRead = 0
            while (bytesRead < stride) {
                val count = reader.read(chunk, bytesRead, stride - bytesRead)
                if (count == -1) break
                bytesRead += count
            }
            if (bytesRead != stride) break

            // Position
            val x = wrapper.getFloat(idxX * 4)
            val y = wrapper.getFloat(idxY * 4)
            val z = wrapper.getFloat(idxZ * 4)
            posBuffer.put(x).put(y).put(z)

            // Bounds
            if (x < bounds.minX) bounds.minX = x; if (x > bounds.maxX) bounds.maxX = x
            if (y < bounds.minY) bounds.minY = y; if (y > bounds.maxY) bounds.maxY = y
            if (z < bounds.minZ) bounds.minZ = z; if (z > bounds.maxZ) bounds.maxZ = z

            // Color (Handle SH or raw Color)
            var r = 0.5f; var g = 0.5f; var b = 0.5f
            if (idxR != -1) {
                // If it's SH, apply coefficient. If raw color, normalize 0-255 if needed (simplified here as float)
                val rawR = wrapper.getFloat(idxR * 4)
                val rawG = wrapper.getFloat(idxG * 4)
                val rawB = wrapper.getFloat(idxB * 4)

                // Simple SH to RGB conversion
                r = (0.5f + SH_C0 * rawR).coerceIn(0f, 1f)
                g = (0.5f + SH_C0 * rawG).coerceIn(0f, 1f)
                b = (0.5f + SH_C0 * rawB).coerceIn(0f, 1f)
            }

            // Opacity (Sigmoid)
            var a = 1.0f
            if (idxOp != -1) {
                val opRaw = wrapper.getFloat(idxOp * 4)
                a = (1.0f / (1.0f + kotlin.math.exp(-opRaw))).coerceIn(0f, 1f)
            }
            colorBuffer.put(r).put(g).put(b).put(a)
        }

        posBuffer.flip()
        colorBuffer.flip()

        return@withContext SplatPointCloud(vertexCount, posBuffer, colorBuffer, bounds)
    }

    private fun readLine(stream: java.io.BufferedInputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        var c: Int
        while (stream.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            bytes.write(c)
        }
        if (bytes.size() == 0 && c == -1) return null
        return bytes.toString(StandardCharsets.US_ASCII.name()).trim()
    }

    private fun maxOf(a: Int, b: Int) = if (a > b) a else b
}