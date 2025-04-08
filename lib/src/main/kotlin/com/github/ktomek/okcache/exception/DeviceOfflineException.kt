package com.github.ktomek.okcache.exception

import java.io.IOException

/**
 * Thrown when the device is detected to be offline and a network request is attempted.
 *
 * This exception is a subclass of [IOException], indicating that a network-related
 * error has occurred. It is specifically used to signal that the device does not
 * have an active network connection, preventing the request from being sent.
 *
 * This exception is typically thrown by the OkCache library when it detects that
 * the device is offline and a network request is required to fulfill a cache miss.
 *
 *
 **/
class DeviceOfflineException(message: String = "Device is offline") : IOException(message)
