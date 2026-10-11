package com.kroombox.konsol.ssh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Jembatan antara thread koneksi SSH dan UI: koneksi mengajukan pertanyaan (host key, password),
 * UI menampilkannya sebagai dialog lalu menjawab.
 */
sealed class Prompt {
    val id: Long = nextId()

    data class HostKey(
        val hostPort: String,
        val keyType: String,
        val fingerprint: String,
        val changed: Boolean,
        val oldFingerprint: String?,
        val answer: CompletableDeferred<Boolean> = CompletableDeferred(),
    ) : Prompt()

    data class Password(
        val title: String,
        val message: String,
        val allowSave: Boolean,
        val answer: CompletableDeferred<PasswordAnswer?> = CompletableDeferred(),
    ) : Prompt()

    companion object {
        private var counter = 0L
        @Synchronized
        private fun nextId(): Long = ++counter
    }
}

data class PasswordAnswer(val password: String, val save: Boolean)

object Prompts {
    private val _pending = MutableStateFlow<List<Prompt>>(emptyList())
    val pending: StateFlow<List<Prompt>> = _pending.asStateFlow()

    private suspend fun <T> ask(prompt: Prompt, deferred: CompletableDeferred<T>, timeoutMs: Long): T? {
        _pending.update { it + prompt }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            _pending.update { list -> list.filterNot { it.id == prompt.id } }
        }
    }

    suspend fun askHostKey(
        hostPort: String, keyType: String, fingerprint: String, changed: Boolean, oldFingerprint: String?,
    ): Boolean {
        val p = Prompt.HostKey(hostPort, keyType, fingerprint, changed, oldFingerprint)
        return ask(p, p.answer, 120_000) ?: false
    }

    suspend fun askPassword(title: String, message: String, allowSave: Boolean = true): PasswordAnswer? {
        val p = Prompt.Password(title, message, allowSave)
        return ask(p, p.answer, 300_000)
    }

    fun answer(prompt: Prompt, value: Any?) {
        when (prompt) {
            is Prompt.HostKey -> prompt.answer.complete(value as? Boolean ?: false)
            is Prompt.Password -> prompt.answer.complete(value as? PasswordAnswer)
        }
    }
}
