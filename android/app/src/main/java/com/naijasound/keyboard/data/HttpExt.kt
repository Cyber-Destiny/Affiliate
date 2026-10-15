package com.naijasound.keyboard.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** Runs the call without blocking a thread; cancelling the coroutine cancels the request. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resumeWith(Result.success(response)) else response.close()
        }
    })
}

class ApiException(val code: Int, message: String) : IOException(message)

class NotConfiguredException : IOException("The app isn't connected to a sound library yet.")
