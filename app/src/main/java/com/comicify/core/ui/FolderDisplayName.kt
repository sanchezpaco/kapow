package com.comicify.core.ui

import android.net.Uri

fun folderDisplayName(treeUri: String): String =
    Uri.decode(treeUri).substringAfterLast(':').substringAfterLast('/').ifEmpty { treeUri }
