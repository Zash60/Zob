package com.zob.recorder.ui.theme

import androidx.lifecycle.ViewModel
import com.zob.recorder.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
// Will be connected to SettingsRepository in Wave 2

@HiltViewModel
class ThemeViewModel @Inject constructor() : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        // TODO: persist via SettingsRepository in Wave 2
    }
}
