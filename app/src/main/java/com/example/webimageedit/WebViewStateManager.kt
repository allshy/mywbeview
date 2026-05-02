package com.example.webimageedit

import android.content.Context
import android.content.SharedPreferences

class WebViewStateManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("webimage_state", Context.MODE_PRIVATE)
    private val providerBackStack = ArrayDeque<String>()

    var currentProviderId: String
        get() = prefs.getString(KEY_PROVIDER_ID, ProviderConfig.default.id) ?: ProviderConfig.default.id
        set(value) {
            prefs.edit().putString(KEY_PROVIDER_ID, value).apply()
        }

    fun recordSwitch(fromProviderId: String?, toProviderId: String) {
        if (!fromProviderId.isNullOrBlank() && fromProviderId != toProviderId) {
            providerBackStack.remove(fromProviderId)
            providerBackStack.addLast(fromProviderId)
        }
        currentProviderId = toProviderId
    }

    fun popPreviousProvider(): String? {
        return providerBackStack.removeLastOrNull()
    }

    companion object {
        private const val KEY_PROVIDER_ID = "provider_id"
    }
}
