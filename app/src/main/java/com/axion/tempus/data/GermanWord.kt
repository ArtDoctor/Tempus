package com.axion.tempus.data

data class GermanWord(
    val german: String,
    val translation: String,
    val forms: List<String> = emptyList()
) {
    val hasForms: Boolean
        get() = forms.isNotEmpty()
}
