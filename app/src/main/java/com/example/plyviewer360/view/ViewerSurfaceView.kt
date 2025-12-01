package com.example.plyviewer360.view

import android.content.Context
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.plyviewer360.renderer.FilamentRenderer

class ViewerSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Choreographer.FrameCallback {

    var renderer: FilamentRenderer? = null
    private val choreographer = Choreographer.getInstance()

    // Gestures
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            renderer?.zoom(1f / d.scaleFactor)
            return true
        }
    })

    init { holder.addCallback(this) }

    override fun surfaceCreated(h: SurfaceHolder) {
        renderer = FilamentRenderer(context, h.surface)
        
        // Try to load the material from assets
        // Update: We now handle loading inside the renderer's logic or passing it here
        // Since I changed renderer.loadMaterial to take only assetName, I must match the signature.
        // BUT wait, in my previous edit to FilamentRenderer, I removed the 'context' parameter from loadMaterial because 
        // I added 'val context: Context' to the constructor of FilamentRenderer.
        
        renderer?.loadMaterial("point_cloud.filamat")
        
        choreographer.postFrameCallback(this)
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
        renderer?.resize(w, ht)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        choreographer.removeFrameCallback(this)
        renderer?.destroy()
        renderer = null
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (renderer != null) {
            renderer?.render(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            lastX = e.x; lastY = e.y
        } else if (e.actionMasked == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress) {
            renderer?.rotate(e.x - lastX, e.y - lastY)
            lastX = e.x; lastY = e.y
        }
        return true
    }
}