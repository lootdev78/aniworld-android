*** Begin Patch
*** Update File: app/src/main/java/io/github/lootdev78/aniworld/PlayerScreen.kt
@@
-import androidx.compose.material.icons.filled.Language
+// Language selection removed from Player UI per request
@@
-    var languageMenuOpen by remember(playback.id) { mutableStateOf(false) }
-    var hosterMenuOpen by remember(playback.id) { mutableStateOf(false) }
+    var hosterMenuOpen by remember(playback.id) { mutableStateOf(false) }
@@
-    val playerOverlayOpen = languageMenuOpen || hosterMenuOpen || castMenuOpen || moreMenuOpen ||
-        manualCastDialogOpen || streamInfoOpen || autoNextVisible || playerError != null
+    val playerOverlayOpen = hosterMenuOpen || castMenuOpen || moreMenuOpen ||
+        manualCastDialogOpen || streamInfoOpen || autoNextVisible || playerError != null
@@
-    val playerLanguages = remember(availableHosters, playback.stream.language) {
-        (availableHosters.map { it.lang } + playback.stream.language).filter { it != Language.UNKNOWN }.distinct()
-    }
+    // language selection removed from UI; keep onLanguageChange callback in signature for compatibility
@@
-        if (languageMenuOpen) {
-            AlertDialog(
-                onDismissRequest = { languageMenuOpen = false; controlsGeneration++ },
-                containerColor = AniWorldPlayerPanel,
-                titleContentColor = Color.White,
-                textContentColor = Color.White,
-                title = { Text(stringResource(R.string.change_language)) },
-                text = {
-                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
-                        items(playerLanguages, key = Language::token) { language ->
-                            TextButton(
-                                onClick = {
-                                    languageMenuOpen = false
-                                    onProgress(position, duration, true)
-                                    stopCastForNavigation()
-                                    onLanguageChange(language)
-                                },
-                                modifier = Modifier.fillMaxWidth()
-                            ) {
-                                if (playback.stream.language == language) Icon(Icons.Default.Check, null, tint = AniWorldPlayerAccent)
-                                Text(language.localizedLabel(), modifier = Modifier.padding(start = 8.dp).weight(1f), color = Color.White)
-                            }
-                        }
-                    }
-                },
-                confirmButton = { TextButton(onClick = { languageMenuOpen = false }) { Text(stringResource(R.string.close), color = AniWorldPlayerAccent) } }
-            )
-        }
+        // language selection removed — no dialog shown
*** End Patch
