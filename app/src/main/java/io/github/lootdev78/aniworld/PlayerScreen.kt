*** Begin Patch
*** Update File: app/src/main/java/io/github/lootdev78/aniworld/PlayerScreen.kt
@@
-import androidx.compose.material.icons.filled.Language
+import androidx.compose.material.icons.filled.Language
+import io.github.lootdev78.aniworld.aniskip.AniskipOverlay
@@
         EmbeddedExoPlayer(
@@
         )
+
+        // Aniskip manual skip overlay (Intro / Outro) — shows manual buttons when available and controlsVisible
+        AniskipOverlay(
+            mediaUrl = playback.stream.url,
+            positionMs = position,
+            onSeekTo = { target -> seekPlayback(target) },
+            visible = controlsVisible
+        )
*** End Patch
