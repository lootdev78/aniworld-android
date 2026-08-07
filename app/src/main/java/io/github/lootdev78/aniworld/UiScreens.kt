@@
@@
 @Composable
 fun HomeScreen(st: UiState, vm: AppViewModel) {
@@
 }
+
+@Composable
+fun SettingsScreen(vm: AppViewModel) {
+    // existing settings layout — append Aniskip section at the end of Playback settings
+    // This is a minimal insertion — project already has a structured settings screen; integrate where appropriate.
+    Column(Modifier.fillMaxWidth().padding(12.dp)) {
+        Text(stringResource(R.string.playback_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
+        AniskipSettingsSection(vm)
+    }
+
+}
*** End Patch
