package com.studyword.literacy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.studyword.literacy.databinding.ItemCharacterCardBinding
import com.studyword.literacy.model.LearningCharacter

class CharacterAdapter(
    private val onMarked: (LearningCharacter, CharacterResult) -> Unit
) : ListAdapter<LearningCharacter, CharacterAdapter.CharacterViewHolder>(DiffCallback) {

    private val selection: MutableMap<Int, CharacterResult> = mutableMapOf()

    fun updateSelections(newSelections: Map<Int, CharacterResult>) {
        selection.clear()
        selection.putAll(newSelections)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCharacterCardBinding.inflate(inflater, parent, false)
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CharacterViewHolder(
        private val binding: ItemCharacterCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var listener: MaterialButtonToggleGroup.OnButtonCheckedListener? = null

        fun bind(character: LearningCharacter) {
            binding.characterText.text = character.hanzi
            binding.pinyinText.text = character.pinyin
            binding.difficultyText.text = character.difficulty.label

            listener?.let { binding.knowledgeGroup.removeOnButtonCheckedListener(it) }
            listener = MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                val result = when (checkedId) {
                    binding.knowButton.id -> CharacterResult.KNOWN
                    binding.unknownButton.id -> CharacterResult.UNKNOWN
                    else -> return@OnButtonCheckedListener
                }
                selection[character.id] = result
                onMarked(character, result)
            }
            binding.knowledgeGroup.addOnButtonCheckedListener(listener!!)

            when (selection[character.id]) {
                CharacterResult.KNOWN -> binding.knowledgeGroup.check(binding.knowButton.id)
                CharacterResult.UNKNOWN -> binding.knowledgeGroup.check(binding.unknownButton.id)
                else -> binding.knowledgeGroup.clearChecked()
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LearningCharacter>() {
            override fun areItemsTheSame(oldItem: LearningCharacter, newItem: LearningCharacter): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LearningCharacter, newItem: LearningCharacter): Boolean =
                oldItem == newItem
        }
    }
}
