package com.epornergay

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class EpornerGayPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(EpornerGayProvider())
    }
}
