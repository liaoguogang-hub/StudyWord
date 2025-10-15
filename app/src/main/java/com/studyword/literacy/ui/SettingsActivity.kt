package com.studyword.literacy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var progressStore: ProgressStore
    private lateinit var repository: CharacterRepository

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportProgress(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        progressStore = ProgressStore(this)

        binding.topBar.setNavigationOnClickListener { finish() }

        binding.libraryButton.setOnClickListener {
            startActivity(Intent(this, CharacterLibraryActivity::class.java))
        }

        binding.exportButton.setOnClickListener {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("识字进度_$date.csv")
        }

        binding.resetButton.setOnClickListener {
            progressStore.reset()
            progressStore.recordSnapshot(0, 0)
            Snackbar.make(binding.root, "进度已重置，回到首页即可重新开始", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun exportProgress(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val known = progressStore.loadKnown()
                    val unknown = progressStore.loadUnknown()
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                            writer.appendLine("汉字,拼音,难度,掌握情况")
                            repository.all().forEach { character ->
                                val status = when {
                                    known.contains(character.id) -> "认识"
                                    unknown.contains(character.id) -> "不认识"
                                    else -> "未测试"
                                }
                                writer.appendLine(
                                    "${character.hanzi}," +
                                        "${character.pinyin}," +
                                        "${character.difficulty.label}," +
                                        status
                                )
                            }
                        }
                    }
                }
                Snackbar.make(binding.root, "导出成功", Snackbar.LENGTH_LONG).show()
            } catch (error: Exception) {
                Snackbar.make(
                    binding.root,
                    "导出失败：${error.localizedMessage}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}
