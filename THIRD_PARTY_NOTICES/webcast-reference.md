# Android WebCast reference

The user supplied the GPLv2-licensed Android WebCast project as a behavioral reference for detecting direct media request URLs in a WebView.

No source code from that project is copied into this application. The Kotlin implementation in `MediaDetection.kt` was written independently and is intentionally limited to ordinary HTTP(S) URLs that already expose a supported media filename or manifest extension.
