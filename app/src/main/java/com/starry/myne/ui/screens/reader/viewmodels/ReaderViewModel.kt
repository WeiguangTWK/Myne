/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.starry.myne.ui.screens.reader.viewmodels

import androidx.annotation.Keep
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starry.myne.R
import com.starry.myne.database.library.LibraryDao
import com.starry.myne.database.reader.ReaderDao
import com.starry.myne.database.reader.ReaderItem
import com.starry.myne.epub.EpubParser
import com.starry.myne.epub.models.EpubBook
import com.starry.myne.ui.theme.figeronaFont
import com.starry.myne.utils.PreferenceUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import javax.inject.Inject


@Keep
sealed class ReaderFont(val id: String, val name: String, val fontFamily: FontFamily) {

    companion object {
        fun getAllFonts() =
            ReaderFont::class.sealedSubclasses.mapNotNull { it.objectInstance }.sortedBy { it.name }

        fun getFontByName(name: String) = getAllFonts().find { it.name == name }!!
    }

    @Keep
    data object System : ReaderFont("system", "System Default", FontFamily.Default)

    @Keep
    data object Serif : ReaderFont("serif", "Serif", FontFamily.Serif)

    @Keep
    data object Cursive : ReaderFont("cursive", "Cursive", FontFamily.Cursive)

    @Keep
    data object SansSerif : ReaderFont("sans-serif", "SansSerif", FontFamily.SansSerif)

    @Keep
    data object Inter : ReaderFont("inter", "Inter", FontFamily(Font(R.font.reader_inter_font)))

    @Keep
    data object Dyslexic :
        ReaderFont("dyslexic", "OpenDyslexic", FontFamily(Font(R.font.reader_inter_font)))

    @Keep
    data object Lora : ReaderFont("figerona", "Figerona", figeronaFont)
}

data class ReaderScreenState(
    val isLoading: Boolean = true,
    val showReaderMenu: Boolean = false,
    val epubBook: EpubBook? = null,
    val readerData: ReaderItem? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val readerDao: ReaderDao,
    private val preferenceUtil: PreferenceUtil,
    private val epubParser: EpubParser
) : ViewModel() {

    var state by mutableStateOf(ReaderScreenState())

    private var _textSize: MutableState<Int> = mutableIntStateOf(getFontSize())
    val textSize: State<Int> = _textSize

    private var _readerFont: MutableState<ReaderFont> = mutableStateOf(getFontFamily())
    val readerFont: State<ReaderFont> = _readerFont

    private val _itemScrolledPercent = mutableFloatStateOf(0f)
    val itemScrolledPercent: State<Float> = _itemScrolledPercent

    fun loadEpubBook(bookId: Int, onLoaded: (ReaderScreenState) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryItem = libraryDao.getItemById(bookId)
            val readerData = readerDao.getReaderItem(bookId)
            // parse and create epub book
            val epubBook = epubParser.createEpubBook(libraryItem!!.filePath)
            state = state.copy(epubBook = epubBook, readerData = readerData)
            onLoaded(state)
            // Added some delay to avoid choppy animation.
            delay(200L)
            state = state.copy(isLoading = false)
        }
    }

    fun loadEpubBookExternal(fileStream: FileInputStream, onLoaded: (EpubBook) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // parse and create epub book
            val epubBook = epubParser.createEpubBook(fileStream, shouldUseToc = false)
            state = state.copy(epubBook = epubBook)
            onLoaded(state.epubBook!!)
            // Added some delay to avoid choppy animation.
            delay(200L)
            state = state.copy(isLoading = false)
        }
    }

    fun updateReaderProgress(bookId: Int, chapterIndex: Int, chapterOffset: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (readerDao.getReaderItem(bookId) != null && chapterIndex != state.epubBook?.chapters!!.size - 1) {
                readerDao.update(bookId, chapterIndex, chapterOffset)
            } else if (chapterIndex == state.epubBook?.chapters!!.size - 1) {
                // if the user has reached last chapter, delete this book
                // from reader database instead of saving it's progress .
                readerDao.getReaderItem(bookId)?.let { readerDao.delete(it.bookId) }
            } else {
                readerDao.insert(readerItem = ReaderItem(bookId, chapterIndex, chapterOffset))
            }
        }
    }

    fun setItemScrolledPercent(percent: Float) {
        _itemScrolledPercent.floatValue = percent
    }

    fun showReaderInfo() {
        state = state.copy(showReaderMenu = true)
    }

    fun hideReaderInfo() {
        state = state.copy(showReaderMenu = false)
    }

    fun setFontFamily(font: ReaderFont) {
        preferenceUtil.putString(PreferenceUtil.READER_FONT_STYLE_STR, font.id)
        _readerFont.value = font
    }

    fun getFontFamily(): ReaderFont {
        return ReaderFont.getAllFonts().find {
            it.id == preferenceUtil.getString(
                PreferenceUtil.READER_FONT_STYLE_STR,
                ReaderFont.System.id
            )
        }!!
    }

    fun setFontSize(newValue: Int) {
        preferenceUtil.putInt(PreferenceUtil.READER_FONT_SIZE_INT, newValue)
        _textSize.value = newValue
    }

    private fun getFontSize() = preferenceUtil.getInt(PreferenceUtil.READER_FONT_SIZE_INT, 100)


}