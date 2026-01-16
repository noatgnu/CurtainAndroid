package info.proteo.curtain.domain.service

import android.webkit.JavascriptInterface

class WebViewJavaScriptBridge(
    private val onPlotReady: () -> Unit = {},
    private val onPlotUpdated: () -> Unit = {},
    private val onPlotError: (String) -> Unit = {},
    private val onWebViewReady: () -> Unit = {},
    private val onPointClicked: (String) -> Unit = {},
    private val onPointHovered: (String) -> Unit = {},
    private val onBarClicked: (String) -> Unit = {},
    private val onBarHover: (String) -> Unit = {},
    private val onImageExported: (String) -> Unit = {},
    private val onPlotDimensions: (String) -> Unit = {},
    private val onAnnotationCoordinates: (String) -> Unit = {},
    private val onPlotExported: (String, String, String, Int, Int) -> Unit = { _, _, _, _, _ -> },
    private val onPlotExportError: (String, String) -> Unit = { _, _ -> },
    private val onPlotInfo: (String) -> Unit = {}
) {

    @JavascriptInterface
    fun onPlotReady(message: String) {
        onPlotReady.invoke()
    }

    @JavascriptInterface
    fun onPlotUpdated(message: String) {
        onPlotUpdated.invoke()
    }

    @JavascriptInterface
    fun onPlotError(message: String) {
        onPlotError.invoke(message)
    }

    @JavascriptInterface
    fun onWebViewReady(message: String) {
        onWebViewReady.invoke()
    }

    @JavascriptInterface
    fun onPointClicked(json: String) {
        onPointClicked.invoke(json)
    }

    @JavascriptInterface
    fun onPointHovered(json: String) {
        onPointHovered.invoke(json)
    }

    @JavascriptInterface
    fun onBarClicked(json: String) {
        onBarClicked.invoke(json)
    }

    @JavascriptInterface
    fun onBarHover(json: String) {
        onBarHover.invoke(json)
    }

    @JavascriptInterface
    fun onImageExported(dataURL: String) {
        onImageExported.invoke(dataURL)
    }

    @JavascriptInterface
    fun onPlotDimensions(json: String) {
        onPlotDimensions.invoke(json)
    }

    @JavascriptInterface
    fun onAnnotationCoordinates(json: String) {
        onAnnotationCoordinates.invoke(json)
    }

    @JavascriptInterface
    fun onPlotExported(format: String, filename: String, dataURL: String, width: Int, height: Int) {
        onPlotExported.invoke(format, filename, dataURL, width, height)
    }

    @JavascriptInterface
    fun onPlotExportError(format: String, error: String) {
        onPlotExportError.invoke(format, error)
    }

    @JavascriptInterface
    fun onPlotInfo(json: String) {
        onPlotInfo.invoke(json)
    }
}
