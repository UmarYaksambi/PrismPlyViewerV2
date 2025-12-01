package com.example.plyviewer360.data

import java.nio.FloatBuffer

/**
 * RAW MEMORY CONTAINER
 * Instead of List<Point>, we use flat FloatBuffers.
 * This is "Zero-Copy" compatible with the GPU.
 */
class SplatPointCloud(
    val splatCount: Int,
    val positionBuffer: FloatBuffer, // x, y, z
    val colorBuffer: FloatBuffer,    // r, g, b, a
    val bounds: AABB
)

// Axis Aligned Bounding Box
data class AABB(
    var minX: Float = Float.MAX_VALUE, var minY: Float = Float.MAX_VALUE, var minZ: Float = Float.MAX_VALUE,
    var maxX: Float = -Float.MAX_VALUE, var maxY: Float = -Float.MAX_VALUE, var maxZ: Float = -Float.MAX_VALUE
) {
    fun center(): FloatArray = floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
    fun maxDimension(): Float = maxOf(maxX - minX, maxY - minY, maxZ - minZ)
}