package com.example.plyviewer360.renderer

import android.content.Context
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.google.android.filament.*
import com.example.plyviewer360.data.SplatPointCloud
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class FilamentRenderer(val context: Context, surface: Surface) {
    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val camera = engine.createCamera(engine.entityManager.create())
    private val swapChain = engine.createSwapChain(surface)

    private var splatEntity: Int = 0
    var material: MaterialInstance? = null

    // Camera Orbit State
    private var camDist = 5f
    private var rotX = 0f
    private var rotY = 0f
    private var center = floatArrayOf(0f, 0f, 0f)

    init {
        view.scene = scene
        view.camera = camera
        // Change clear color to Dark Grey to distinguish from "screen off" or "black points"
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0.1f, 0.1f, 0.1f, 1f)
        }
    }

    // Helper to load material from context
    fun loadMaterial(assetName: String) {
         try {
            val buffer = context.assets.open(assetName).use { input ->
                val bytes = ByteArray(input.available())
                input.read(bytes)
                ByteBuffer.wrap(bytes)
            }
            val mat = Material.Builder()
                .payload(buffer, buffer.remaining())
                .build(engine)
            
            material = mat.defaultInstance
            Log.d("FilamentRenderer", "Material loaded successfully")

        } catch (e: Exception) {
            Log.e("FilamentRenderer", "Could not load material: $assetName", e)
            // Post a toast to the main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Error: 'point_cloud.filamat' missing in assets.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun loadSplats(cloud: SplatPointCloud) {
        if (splatEntity != 0) {
            scene.remove(splatEntity)
            engine.destroyEntity(splatEntity)
        }

        splatEntity = EntityManager.get().create()

        // 1. Vertex Buffer (Positions + Colors)
        val vb = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(cloud.splatCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .build(engine)

        vb.setBufferAt(engine, 0, cloud.positionBuffer)
        vb.setBufferAt(engine, 1, cloud.colorBuffer)

        // 2. Index Buffer (Simple 0..N)
        val ibData = ByteBuffer.allocateDirect(cloud.splatCount * 4).order(ByteOrder.nativeOrder())
        val intBuf = ibData.asIntBuffer()
        for(i in 0 until cloud.splatCount) intBuf.put(i)

        val ib = IndexBuffer.Builder()
            .indexCount(cloud.splatCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        ib.setBuffer(engine, ibData)

        // 3. Renderable
        val dim = cloud.bounds.maxDimension()
        
        val renderableBuilder = RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, dim, dim, dim))
            .geometry(0, RenderableManager.PrimitiveType.POINTS, vb, ib)
            .culling(false)

        if (material != null) {
            renderableBuilder.material(0, material!!)
            renderableBuilder.build(engine, splatEntity)
            scene.addEntity(splatEntity)
            Log.d("FilamentRenderer", "Splats loaded. Entity: $splatEntity. Points: ${cloud.splatCount}")
        } else {
             Log.e("FilamentRenderer", "CANNOT RENDER: Material is null.")
             android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Error: Point cloud format missing (Material not found)", Toast.LENGTH_LONG).show()
            }
        }

        // Reset Camera
        center = cloud.bounds.center()
        camDist = dim * 1.5f
        updateCamera()
    }

    private fun updateCamera() {
        val radX = Math.toRadians(rotX.toDouble())
        val radY = Math.toRadians(rotY.toDouble())
        val y = camDist * sin(radX)
        val r = camDist * cos(radX)
        val x = r * sin(radY)
        val z = r * cos(radY)

        camera.lookAt(
            center[0] + x, center[1] + y, center[2] + z,
            center[0].toDouble(), center[1].toDouble(), center[2].toDouble(),
            0.0, 1.0, 0.0
        )
    }

    fun render(frameTime: Long) {
        if (renderer.beginFrame(swapChain!!, frameTime)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun resize(w: Int, h: Int) {
        view.viewport = Viewport(0, 0, w, h)
        camera.setProjection(60.0, w.toDouble()/h.toDouble(), 0.1, 1000.0, Camera.Fov.VERTICAL)
    }

    fun rotate(dx: Float, dy: Float) {
        rotY += dx * 0.3f
        rotX = (rotX + dy * 0.3f).coerceIn(-89f, 89f)
        updateCamera()
    }

    fun zoom(factor: Float) {
        camDist = (camDist * factor).coerceAtLeast(0.1f)
        updateCamera()
    }

    fun destroy() { engine.destroy() }
}