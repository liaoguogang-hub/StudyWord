package com.studyword.literacy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
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
    private lateinit var englishRepository: EnglishRepository

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
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore(this)

        binding.topBar.setNavigationOnClickListener { finish() }

        binding.libraryButton.setOnClickListener {
            startActivity(Intent(this, CharacterLibraryActivity::class.java))
        }

        binding.exportButton.setOnClickListener {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("StudyWord_$date.csv")
        }

        // 重置中文进度
        binding.resetChineseButton.setOnClickListener {
            progressStore.save(emptySet(), emptySet())
            progressStore.recordSnapshot(0, 0)
            Snackbar.make(binding.root, "中文进度已重置", Snackbar.LENGTH_LONG).show()
            refreshOverview()
        }

        // 重置英文进度(保留中文)
        binding.resetEnglishButton.setOnClickListener {
            progressStore.resetEnglish()
            Snackbar.make(binding.root, "English progress reset", Snackbar.LENGTH_LONG).show()
            refreshOverview()
        }

        refreshOverview()
    }

    /**
     * 刷新字库总览卡片(中英分别)
     */
    private fun refreshOverview() {
        val chineseTotal = repository.count()
        val chineseKnown = progressStore.loadKnown().size
        val chineseUnknown = progressStore.loadUnknown().size
        binding.overviewChinese.text = "中文 $chineseTotal 字"
        binding.overviewChineseProgress.text =
            "中文已学: $chineseKnown · 待巩固: $chineseUnknown"

        val letterCount = englishRepository.letterCount()
        val wordCount = englishRepository.wordCount()
        val englishKnown = progressStore.loadEnglishKnown().size
        val englishUnknown = progressStore.loadEnglishUnknown().size
        binding.overviewEnglish.text =
            "English: $letterCount letters · $wordCount words"
        binding.overviewEnglishProgress.text =
            "English: Known $englishKnown · Review $englishUnknown"
    }

    /**
     * 导出 CSV,中英两段拼成同一文件:
     * 段 1:中文 known/unknown 表(兼容 v1.2.1 列)
     * 段 2:English letters + words
     */
    private fun exportProgress(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val chineseKnown = progressStore.loadKnown()
                    val chineseUnknown = progressStore.loadUnknown()
                    val englishKnown = progressStore.loadEnglishKnown()
                    val englishUnknown = progressStore.loadEnglishUnknown()
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                            // ====== 段 1:中文 ======
                            writer.appendLine("# Chinese characters")
                            writer.appendLine("汉字,拼音,难度,掌握情况")
                            repository.all().forEach { character ->
                                val status = when {
                                    chineseKnown.contains(character.id) -> "认识"
                                    chineseUnknown.contains(character.id) -> "不认识"
                                    else -> "未测试"
                                }
                                writer.appendLine(
                                    "${character.hanzi}," +
                                        "${character.pinyin}," +
                                        "${character.difficulty.label}," +
                                        status
                                )
                            }
                            writer.appendLine()

                            // ====== 段 2:English letters ======
                            writer.appendLine("# English letters")
                            writer.appendLine("Letter,Uppercase,Lowercase,Phonetic,Example,Status")
                            val letterCount = englishRepository.letterCount()
                            englishRepository.letters().forEach { letter ->
                                // id 范围 [0, letterCount-1]
                                val status = when {
                                    englishKnown.contains(letter.id) -> "known"
                                    englishUnknown.contains(letter.id) -> "review"
                                    else -> "new"
                                }
                                writer.appendLine(
                                    listOf(
                                        letter.letter,
                                        letter.uppercase,
                                        letter.lowercase,
                                        letter.phonetic,
                                        letter.exampleWord,
                                        status
                                    ).joinToString(",")
                                )
                            }
                            writer.appendLine()

                            // ====== 段 3:English words ======
                            writer.appendLine("# English words")
                            writer.appendLine("Word,Phonetic,ChineseMeaning,ExampleSentence,Status")
                            englishRepository.words().forEach { word ->
                                // word.id = EnglishRepository 中的序号 (0~wordCount-1)
                                val status = when {
                                    englishKnown.contains(word.id) -> "known"
                                    englishUnknown.contains(word.id) -> "review"
                                    else -> "new"
                                }
                                writer.appendLine(
                                    listOf(
                                        word.word,
                                        word.phonetic,
                                        word.chineseMeaning,
                                        word.exampleSentence,
                                        status
                                    ).joinToString(",")
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
