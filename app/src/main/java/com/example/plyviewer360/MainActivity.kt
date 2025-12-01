package com.example.plyviewer360

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plyviewer360.databinding.ActivityMainBinding
import com.example.plyviewer360.parser.PlyParser
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadPly(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fabLoad.setOnClickListener {
            filePicker.launch(arrayOf("application/octet-stream", "*/*"))
        }

        binding.btnBack.setOnClickListener {
            clearViewer()
        }
    }

    private fun loadPly(uri: Uri) {
        // UI State: Loading
        binding.loadingLayout.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.status_preparing)
        binding.fabLoad.hide()
        binding.btnBack.visibility = View.GONE
        binding.welcomeLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    binding.tvStatus.text = getString(R.string.status_parsing, 0)
                    
                    // Parse in background with progress update
                    val splatCloud = PlyParser.parse(stream) { progress ->
                        runOnUiThread {
                             val percent = (progress * 100).toInt()
                             binding.tvStatus.text = getString(R.string.status_parsing, percent)
                             binding.progressBar.isIndeterminate = false
                             binding.progressBar.progress = percent
                        }
                    }

                    // Reset progress bar for rendering (which is currently indeterminate or blocking)
                    runOnUiThread {
                        binding.tvStatus.text = getString(R.string.status_rendering)
                        binding.progressBar.isIndeterminate = true
                    }
                    
                    // Render
                    binding.viewer.renderer?.loadSplats(splatCloud)

                    // UI State: Loaded
                    binding.loadingLayout.visibility = View.GONE
                    binding.btnBack.visibility = View.VISIBLE
                    
                    Toast.makeText(this@MainActivity, getString(R.string.status_loaded, splatCloud.splatCount), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.loadingLayout.visibility = View.GONE
                binding.fabLoad.show()
                binding.welcomeLayout.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearViewer() {
        binding.viewer.renderer?.clear()
        binding.btnBack.visibility = View.GONE
        binding.fabLoad.show()
        binding.welcomeLayout.visibility = View.VISIBLE
    }
}