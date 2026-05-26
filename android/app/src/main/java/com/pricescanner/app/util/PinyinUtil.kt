package com.pricescanner.app.util

import android.content.Context
import java.nio.charset.Charset
import com.pricescanner.app.R

object PinyinUtil {
    private lateinit var initials: CharArray
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        val bytes = context.resources.openRawResource(R.raw.pinyin_data)
            .readBytes()
        val text = String(bytes, Charset.forName("UTF-8"))
        initials = CharArray(0x9FFF - 0x4E00 + 1) { '?' }
        val len = minOf(text.length, initials.size)
        for (i in 0 until len) {
            initials[i] = text[i]
        }
        isLoaded = true
    }

    fun getInitials(text: String): String {
        if (!isLoaded) return ""
        val sb = StringBuilder(text.length)
        for (char in text) {
            val code = char.code
            when {
                code in 0x4E00..0x9FFF -> sb.append(initials[code - 0x4E00])
                char.isLetter() -> sb.append(char.lowercaseChar())
            }
        }
        return sb.toString()
    }
}
