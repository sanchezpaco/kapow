package com.comicify

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.comicify.core.ui.theme.ComicifyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = resolveViewIntentUri()
        setContent {
            ComicifyTheme(useDarkTheme = true) {
                ComicifyRoot(initialUri = initialUri)
            }
        }
    }

    private fun resolveViewIntentUri(): Uri? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        takePersistableReadPermissionIfGranted(uri)
        return uri
    }

    private fun takePersistableReadPermissionIfGranted(uri: Uri) {
        val persistableGrant = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || !persistableGrant) return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
