package com.glinboy.onair

actual fun createMediaMonitor(): MediaMonitor = FakeMediaMonitor()
