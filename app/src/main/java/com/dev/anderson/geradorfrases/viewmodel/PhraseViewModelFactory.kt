package com.dev.anderson.geradorfrases.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dev.anderson.geradorfrases.data.PhraseViewModel
import com.dev.anderson.geradorfrases.repository.PhraseRepository

class PhraseViewModelFactory(private val repository: PhraseRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhraseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PhraseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}