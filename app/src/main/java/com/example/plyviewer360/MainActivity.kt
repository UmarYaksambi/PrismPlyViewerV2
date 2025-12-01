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
            // Open file picker
            filePicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
    }

    private fun loadPly(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    Toast.makeText(this@MainActivity, "Parsing...", Toast.LENGTH_SHORT).show()

                    // Parse in background
                    val splatCloud = PlyParser.parse(stream)

                    // Render
                    binding.viewer.renderer?.loadSplats(splatCloud)

                    binding.progressBar.visibility = View.GONE
                    binding.fabLoad.hide()
                    Toast.makeText(this@MainActivity, "Loaded ${splatCloud.splatCount} points", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}