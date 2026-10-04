package com.najmulcodes.zapflick.ui.viewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.data.viewer.DocumentInfoReader
import com.najmulcodes.zapflick.data.viewer.HtmlDocumentReader
import com.najmulcodes.zapflick.data.viewer.PdfSession
import com.najmulcodes.zapflick.data.viewer.PdfSessionFactory
import com.najmulcodes.zapflick.domain.viewer.DocumentInfo
import com.najmulcodes.zapflick.domain.viewer.DocumentType
import com.najmulcodes.zapflick.domain.viewer.DocumentTypeResolver
import com.najmulcodes.zapflick.domain.viewer.HtmlViewOptions
import com.najmulcodes.zapflick.domain.viewer.ViewerError
import com.najmulcodes.zapflick.domain.viewer.ViewerException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val infoReader: DocumentInfoReader,
    private val sessionFactory: PdfSessionFactory,
    private val htmlReader: HtmlDocumentReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    /**
     * The HTML switches. They live only as long as this screen: they are not written to
     * [SavedStateHandle] or to disk, so every file starts with scripts and online content off,
     * and a rotation keeps what the person chose.
     */
    private val _htmlOptions = MutableStateFlow(HtmlViewOptions())
    val htmlOptions: StateFlow<HtmlViewOptions> = _htmlOptions.asStateFlow()

    private var started = false
    private var session: PdfSession? = null

    /**
     * The document being shown. It lives in [SavedStateHandle] so it survives process death. The
     * permission that came with the Intent does not, which is why opening can end in
     * [ViewerError.AccessLost].
     */
    val documentUri: Uri? get() = savedStateHandle.get<Uri>(KEY_URI)

    /** Only content: links can be passed on; handing a file: link to another app throws. */
    val canHandOff: Boolean get() = documentUri?.scheme == ContentResolver.SCHEME_CONTENT

    /** Safe to call on every onCreate: a rotation or a restored process keeps the first document. */
    fun start(intentUri: Uri?, intentMimeType: String?) {
        if (started) return
        started = true

        val uri = savedStateHandle.get<Uri>(KEY_URI)
            ?: intentUri?.also { savedStateHandle[KEY_URI] = it }
        if (uri == null) {
            _uiState.value = ViewerUiState.Error(ViewerError.NoDocument, null, DocumentType.Unsupported)
            return
        }
        viewModelScope.launch { load(uri, intentMimeType) }
    }

    fun setScriptsEnabled(enabled: Boolean) = _htmlOptions.update { it.copy(scriptsEnabled = enabled) }

    fun setOnlineContentAllowed(allowed: Boolean) = _htmlOptions.update { it.copy(onlineContentAllowed = allowed) }

    fun setShowSource(show: Boolean) = _htmlOptions.update { it.copy(showSource = show) }

    fun cachedPage(index: Int, widthPx: Int): Bitmap? = session?.cachedPage(index, widthPx)

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = session?.renderPage(index, widthPx)

    private suspend fun load(uri: Uri, intentMimeType: String?) {
        _uiState.value = ViewerUiState.Loading
        var info: DocumentInfo? = null
        var type = DocumentType.Unsupported
        try {
            if (infoReader.isOwnPrivateFile(uri)) throw ViewerException(ViewerError.NotReadable)

            val read = infoReader.read(uri)
            info = read
            type = DocumentTypeResolver.resolveFirst(read.displayName, intentMimeType, read.mimeType)
            when (type) {
                DocumentType.Pdf -> openPdf(uri, read)
                DocumentType.Html -> openHtml(uri, read)
                DocumentType.Unsupported -> throw ViewerException(ViewerError.Unsupported)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ViewerException) {
            _uiState.value = ViewerUiState.Error(e.error, info, type)
        } catch (e: SecurityException) {
            _uiState.value = ViewerUiState.Error(ViewerError.AccessLost, info, type)
        } catch (e: IOException) {
            _uiState.value = ViewerUiState.Error(ViewerError.NotReadable, info, type)
        }
    }

    private suspend fun openPdf(uri: Uri, info: DocumentInfo) {
        val opened = sessionFactory.open(uri)
        session = opened
        _uiState.value = ViewerUiState.Pdf(info, opened.pages)
    }

    private suspend fun openHtml(uri: Uri, info: DocumentInfo) {
        val html = htmlReader.read(uri, info.sizeBytes)
        _uiState.value = ViewerUiState.Html(info, html)
    }

    override fun onCleared() {
        session?.close()
        session = null
    }

    private companion object {
        const val KEY_URI = "document_uri"
    }
}
