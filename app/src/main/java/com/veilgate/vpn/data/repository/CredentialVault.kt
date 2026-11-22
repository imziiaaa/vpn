package com.veilgate.vpn.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.veilgate.vpn.data.network.MobileCredentials
import okhttp3.HttpUrl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class CredentialVault(context: Context, origin: HttpUrl) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "raven-mobile-credentials"))
    private val originBinding = "${origin.scheme}://${origin.host}:${origin.port}".toByteArray()
    private val keyAlias = "raven.mobile.credentials.v1"

    init {
        require(origin.isHttps && origin.username.isEmpty() && origin.password.isEmpty())
    }

    fun read(): MobileCredentials? = storageOperation {
        val ciphertext = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            if (file.baseFile.exists()) throw IllegalStateException("Cannot read credentials")
            return@storageOperation null
        }
        check(ciphertext.size > IV_BYTES)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key(create = false),
            GCMParameterSpec(128, ciphertext, 0, IV_BYTES))
        cipher.updateAAD(originBinding)
        DataInputStream(ByteArrayInputStream(cipher.doFinal(ciphertext, IV_BYTES,
            ciphertext.size - IV_BYTES))).use { input ->
            MobileCredentials(input.readUTF(), input.readUTF()).also { check(input.read() == -1) }
        }
    }

    fun write(credentials: MobileCredentials) = storageOperation {
        val plain = ByteArrayOutputStream()
        DataOutputStream(plain).use {
            it.writeUTF(credentials.accessToken)
            it.writeUTF(credentials.refreshToken)
        }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(originBinding)
        check(cipher.iv.size == IV_BYTES)
        val output = file.startWrite()
        try {
            output.write(cipher.iv)
            output.write(cipher.doFinal(plain.toByteArray()))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        // AtomicFile reports failed sync/rename through the platform log.
        check(read()?.run {
            accessToken == credentials.accessToken && refreshToken == credentials.refreshToken
        } == true)
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val current = store.getKey(keyAlias, null)
        if (current != null) return current as SecretKey
        check(create) { "Credential encryption key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private inline fun <T> storageOperation(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        throw CredentialStorageException()
    }

    private companion object {
        const val CIPHER = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

internal class CredentialStorageException : IllegalStateException(
    "Raven VPN couldn't access secure storage. Unlock the device and retry. If it persists, clear Raven VPN app storage and set it up again."
)
