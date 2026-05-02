package com.example.webimageedit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var stateManager: WebViewStateManager
    private lateinit var downloadHandler: DownloadHandler
    private lateinit var webContainer: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var navigationBar: LinearLayout
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var tuneBar: LinearLayout
    private lateinit var modeButton: Button
    private lateinit var pageZoomLabel: TextView
    private lateinit var textZoomLabel: TextView

    private val webViews = linkedMapOf<String, WebView>()
    private val navButtons = linkedMapOf<String, Button>()
    private var currentProvider: ProviderConfig = ProviderConfig.default
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingFileChooserRequest: FileChooserRequest? = null
    private var mediaPermissionPrompted = false
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stateManager = WebViewStateManager(this)
        downloadHandler = DownloadHandler(this)
        settingsPrefs = getSharedPreferences("provider_tuning", MODE_PRIVATE)
        currentProvider = ProviderConfig.byId(stateManager.currentProviderId)

        configureCookies()
        buildLayout()
        switchToProvider(currentProvider, addToBackStack = false)
    }

    private fun configureCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color("surface"))
        }
        applySystemBarInsets(root)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        root.addView(
            progressBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        )

        val tuneScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(color("panel"))
        }
        tuneBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        tuneScroll.addView(
            tuneBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        buildTuneBar()
        root.addView(
            tuneScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
        )

        webContainer = FrameLayout(this)
        root.addView(
            webContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        navigationBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(8))
            setBackgroundColor(color("panel"))
        }
        root.addView(
            navigationBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
        )

        ProviderConfig.all.forEach { provider ->
            val button = Button(this).apply {
                text = provider.shortLabel
                isAllCaps = false
                textSize = 13f
                setSingleLine()
                setOnClickListener { switchToProvider(provider, addToBackStack = true) }
            }
            navButtons[provider.id] = button
            navigationBar.addView(
                button,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }

        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
        }
        errorText = TextView(this).apply {
            text = "页面加载失败"
            textSize = 16f
            setTextColor(color("text"))
            gravity = Gravity.CENTER
        }
        val retryButton = Button(this).apply {
            text = "重试"
            isAllCaps = false
            setOnClickListener {
                errorPanel.visibility = View.GONE
                currentWebView()?.reload()
            }
        }
        errorPanel.addView(
            errorText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        errorPanel.addView(
            retryButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        webContainer.addView(
            errorPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    private fun switchToProvider(provider: ProviderConfig, addToBackStack: Boolean) {
        if (provider.id == currentProvider.id && currentWebView() != null) {
            return
        }

        val previousProviderId = currentProvider.id
        currentProvider = provider
        if (addToBackStack) {
            stateManager.recordSwitch(previousProviderId, provider.id)
        } else {
            stateManager.currentProviderId = provider.id
        }

        ProviderConfig.all.forEach { config ->
            webViews[config.id]?.visibility = if (config.id == provider.id) View.VISIBLE else View.GONE
        }

        val webView = webViews[provider.id] ?: createWebView(provider).also {
            webViews[provider.id] = it
            webContainer.addView(
                it,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            it.loadUrl(provider.url)
        }
        webView.visibility = View.VISIBLE
        webView.requestFocus()

        updateNavigationSelection()
        updateTuneControls()
        errorPanel.visibility = View.GONE
    }

    private fun buildTuneBar() {
        modeButton = tuneButton("PC") {
            val settings = currentTuning()
            val nextMode = if (settings.userAgentMode == UserAgentMode.DESKTOP) {
                UserAgentMode.MOBILE
            } else {
                UserAgentMode.DESKTOP
            }
            saveTuning(settings.copy(userAgentMode = nextMode))
            currentWebView()?.let {
                applyWebSettings(it, currentProvider)
                it.reload()
            }
            updateTuneControls()
        }
        tuneBar.addView(modeButton)

        tuneBar.addView(tuneButton("页-") { adjustPageZoom(-10) })
        pageZoomLabel = tuneLabel("页100%")
        tuneBar.addView(pageZoomLabel)
        tuneBar.addView(tuneButton("页+") { adjustPageZoom(10) })

        tuneBar.addView(tuneButton("字-") { adjustTextZoom(-10) })
        textZoomLabel = tuneLabel("字100%")
        tuneBar.addView(textZoomLabel)
        tuneBar.addView(tuneButton("字+") { adjustTextZoom(10) })

        tuneBar.addView(tuneButton("刷新") {
            currentWebView()?.reload()
        })
        tuneBar.addView(tuneButton("重置") {
            resetTuning(currentProvider)
            currentWebView()?.let {
                applyWebSettings(it, currentProvider)
                it.reload()
            }
            updateTuneControls()
        })
    }

    private fun tuneButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            minWidth = 0
            minHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { action() }
            tuneBarLayoutParams().also { layoutParams = it }
        }
    }

    private fun tuneLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(color("text"))
            minWidth = dp(58)
            tuneBarLayoutParams().also { layoutParams = it }
        }
    }

    private fun tuneBarLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun updateTuneControls() {
        val settings = currentTuning()
        modeButton.text = if (settings.userAgentMode == UserAgentMode.DESKTOP) "PC" else "手机"
        pageZoomLabel.text = "页${settings.pageZoom}%"
        textZoomLabel.text = "字${settings.textZoom}%"
    }

    private fun adjustPageZoom(delta: Int) {
        val settings = currentTuning()
        val next = settings.copy(pageZoom = clamp(settings.pageZoom + delta, 50, 160))
        saveTuning(next)
        currentWebView()?.let {
            applyWebSettings(it, currentProvider)
            injectProviderStyles(it, currentProvider)
        }
        updateTuneControls()
    }

    private fun adjustTextZoom(delta: Int) {
        val settings = currentTuning()
        val next = settings.copy(textZoom = clamp(settings.textZoom + delta, 70, 180))
        saveTuning(next)
        currentWebView()?.let {
            applyWebSettings(it, currentProvider)
            injectProviderStyles(it, currentProvider)
        }
        updateTuneControls()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(provider: ProviderConfig): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = provider.loadWithOverviewMode
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            applyWebSettings(this, provider)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            webViewClient = ProviderWebViewClient(provider)
            webChromeClient = ProviderChromeClient(provider)
            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                downloadHandler.enqueue(url, userAgent, contentDisposition, mimeType)
            })
        }
    }

    private inner class ProviderWebViewClient(private val provider: ProviderConfig) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request == null) return false
            return handleUrl(request.url.toString(), provider)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null) return false
            return handleUrl(url, provider)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            CookieManager.getInstance().flush()
            view?.let { injectProviderStyles(it, provider) }
            progressBar.visibility = View.GONE
            if (provider.id == currentProvider.id) {
                errorPanel.visibility = View.GONE
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (request?.isForMainFrame == true && provider.id == currentProvider.id) {
                showLoadError(error?.description?.toString().orEmpty())
            }
        }

        private fun handleUrl(url: String, provider: ProviderConfig): Boolean {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
                return false
            }
            if (scheme != "http" && scheme != "https") {
                openExternal(url)
                return true
            }

            return if (provider.canOpenInside(url)) {
                false
            } else {
                openExternal(url)
                true
            }
        }
    }

    private inner class ProviderChromeClient(private val provider: ProviderConfig) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (provider.id != currentProvider.id) {
                return
            }
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = filePathCallback
            val request = FileChooserRequest(
                acceptTypes = fileChooserParams.acceptTypes,
                allowMultiple = fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            )
            if (shouldRequestMediaAccess(request)) {
                pendingFileChooserRequest = request
                requestPermissions(mediaAccessPermissions(), REQUEST_MEDIA_PERMISSION)
            } else {
                openFileChooser(request)
            }
            return true
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            if (resultMsg == null) return false

            val popupWebView = WebView(this@MainActivity).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return request?.url?.toString()?.let { routePopupUrl(it, provider) } ?: false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return url?.let { routePopupUrl(it, provider) } ?: false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (url?.let { routePopupUrl(it, provider) } == true) {
                            view?.destroy()
                        }
                    }
                }
            }

            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popupWebView
            resultMsg.sendToTarget()
            return true
        }
    }

    private fun routePopupUrl(url: String, provider: ProviderConfig): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
            return false
        }

        if ((scheme == "http" || scheme == "https") && provider.canOpenInside(url)) {
            webViews[provider.id]?.loadUrl(url)
        } else {
            openExternal(url)
        }
        return true
    }

    private fun applySystemBarInsets(root: View) {
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            root.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                top = systemBars.top
                bottom = systemBars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(0, top, 0, bottom)
            insets
        }
        root.post { root.requestApplyInsets() }
    }

    private fun injectProviderStyles(webView: WebView, provider: ProviderConfig) {
        val tuning = tuningFor(provider)
        val css = provider.injectedCss
            .replace("\\", "\\\\")
            .replace("`", "\\`")
        val script = """
            (function() {
                var id = 'webimage-edit-provider-style';
                var old = document.getElementById(id);
                if (old) old.remove();
                var style = document.createElement('style');
                style.id = id;
                style.textContent = `html { zoom: ${tuning.pageZoom}%; } $css`;
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun applyWebSettings(webView: WebView, provider: ProviderConfig) {
        val tuning = tuningFor(provider)
        webView.settings.textZoom = tuning.textZoom
        webView.settings.userAgentString = when (tuning.userAgentMode) {
            UserAgentMode.DESKTOP -> desktopUserAgent
            UserAgentMode.MOBILE -> WebSettings.getDefaultUserAgent(this)
        }
        webView.setInitialScale(100)
    }

    private fun currentTuning(): ProviderTuning {
        return tuningFor(currentProvider)
    }

    private fun tuningFor(provider: ProviderConfig): ProviderTuning {
        val modeName = settingsPrefs.getString("${provider.id}_ua", provider.userAgentMode.name)
        val mode = runCatching { UserAgentMode.valueOf(modeName ?: provider.userAgentMode.name) }
            .getOrDefault(provider.userAgentMode)
        return ProviderTuning(
            providerId = provider.id,
            userAgentMode = mode,
            pageZoom = settingsPrefs.getInt("${provider.id}_page_zoom", provider.initialScale),
            textZoom = settingsPrefs.getInt("${provider.id}_text_zoom", provider.textZoom)
        )
    }

    private fun saveTuning(tuning: ProviderTuning) {
        settingsPrefs.edit()
            .putString("${tuning.providerId}_ua", tuning.userAgentMode.name)
            .putInt("${tuning.providerId}_page_zoom", tuning.pageZoom)
            .putInt("${tuning.providerId}_text_zoom", tuning.textZoom)
            .apply()
    }

    private fun resetTuning(provider: ProviderConfig) {
        settingsPrefs.edit()
            .remove("${provider.id}_ua")
            .remove("${provider.id}_page_zoom")
            .remove("${provider.id}_text_zoom")
            .apply()
    }

    private fun openFileChooser(request: FileChooserRequest) {
        val acceptsImage = acceptsImage(request.acceptTypes)
        val allowMultiple = request.allowMultiple

        val intents = mutableListOf<Intent>()
        if (acceptsImage && packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            createCameraIntent()?.let(intents::add)
        }

        val pickerIntent = if (acceptsImage) {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = acceptedMimeType(request.acceptTypes)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        }

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, pickerIntent)
            putExtra(Intent.EXTRA_TITLE, "选择图片")
            if (intents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
            }
        }

        try {
            startActivityForResult(chooser, REQUEST_FILE_CHOOSER)
        } catch (error: ActivityNotFoundException) {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = null
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldRequestMediaAccess(request: FileChooserRequest): Boolean {
        if (!request.acceptsImage || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        return !mediaPermissionPrompted && !hasFullMediaAccess()
    }

    private fun hasFullMediaAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun mediaAccessPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun createCameraIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return null
        }

        val uri = createCameraImageUri() ?: return null
        pendingCameraUri = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun createCameraImageUri(): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "webimage_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WebImageEdit")
            }
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "未授予相机权限，仍可从相册选择图片", Toast.LENGTH_SHORT).show()
        }
        if (requestCode == REQUEST_MEDIA_PERMISSION) {
            val request = pendingFileChooserRequest
            mediaPermissionPrompted = true
            pendingFileChooserRequest = null
            if (request != null) {
                if (!hasFullMediaAccess() && request.acceptsImage) {
                    Toast.makeText(this, "未获得完整相册权限，将使用系统文件选择器", Toast.LENGTH_SHORT).show()
                }
                openFileChooser(request)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE_CHOOSER) {
            return
        }

        val results = if (resultCode == RESULT_OK) {
            collectChosenUris(data)
        } else {
            null
        }
        uploadCallback?.onReceiveValue(results)
        uploadCallback = null
        pendingCameraUri = null
    }

    private fun collectChosenUris(data: Intent?): Array<Uri>? {
        val clipData = data?.clipData
        if (clipData != null && clipData.itemCount > 0) {
            return Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
        }
        data?.data?.let { return arrayOf(it) }
        pendingCameraUri?.let { return arrayOf(it) }
        return null
    }

    override fun onBackPressed() {
        val webView = currentWebView()
        when {
            webView?.canGoBack() == true -> webView.goBack()
            else -> {
                val previousProviderId = stateManager.popPreviousProvider()
                if (previousProviderId != null) {
                    switchToProvider(ProviderConfig.byId(previousProviderId), addToBackStack = false)
                } else {
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    private fun currentWebView(): WebView? = webViews[currentProvider.id]

    private fun updateNavigationSelection() {
        navButtons.forEach { (id, button) ->
            val selected = id == currentProvider.id
            button.isSelected = selected
            button.setTextColor(if (selected) color("accent") else color("muted"))
            button.setBackgroundColor(if (selected) 0xFFEAF1FF.toInt() else Color.TRANSPARENT)
        }
    }

    private fun showLoadError(description: String) {
        errorText.text = if (description.isBlank()) {
            "${currentProvider.title} 页面加载失败"
        } else {
            "${currentProvider.title} 页面加载失败\n$description"
        }
        progressBar.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
        errorPanel.bringToFront()
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun acceptsImage(acceptTypes: Array<String>): Boolean {
        return acceptTypes.isEmpty() ||
            acceptTypes.any { it.isBlank() || it == "*/*" || it.startsWith("image/") }
    }

    private fun acceptedMimeType(acceptTypes: Array<String>): String {
        val usable = acceptTypes.firstOrNull { it.isNotBlank() && !it.contains(",") }
        return usable ?: "image/*"
    }

    private fun color(name: String): Int {
        return when (name) {
            "surface" -> 0xFFF7F8FA.toInt()
            "panel" -> 0xFFFFFFFF.toInt()
            "text" -> 0xFF16181D.toInt()
            "muted" -> 0xFF6D7480.toInt()
            "accent" -> 0xFF176BFF.toInt()
            else -> Color.BLACK
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return value.coerceIn(min, max)
    }

    companion object {
        private const val REQUEST_FILE_CHOOSER = 1001
        private const val REQUEST_CAMERA_PERMISSION = 1002
        private const val REQUEST_MEDIA_PERMISSION = 1003
    }
}

private data class FileChooserRequest(
    val acceptTypes: Array<String>,
    val allowMultiple: Boolean
) {
    val acceptsImage: Boolean
        get() = acceptTypes.isEmpty() ||
            acceptTypes.any { it.isBlank() || it == "*/*" || it.startsWith("image/") }
}

private data class ProviderTuning(
    val providerId: String,
    val userAgentMode: UserAgentMode,
    val pageZoom: Int,
    val textZoom: Int
)
