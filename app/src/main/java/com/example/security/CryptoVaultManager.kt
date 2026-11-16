package com.example.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedArtifactResult(
    val localVaultPath: String,
    val sha256Digest: String,
    val storagePath: String,
    val encryptionAlgo: String = "AES-256-GCM",
)

class CryptoVaultManager(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, "evidence_vault").apply { mkdirs() }
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    suspend fun encryptAndStoreBytes(
        rawBytes: ByteArray,
        extension: String,
        userId: String,
        uploadToCloudStorage: Boolean,
    ): Result<EncryptedArtifactResult> = withContext(Dispatchers.IO) {
        runCatching {
            val digest = sha256Hex(rawBytes)
            val encryptedBlob = encryptAesGcm(rawBytes)
            val fileName = "ev_${UUID.randomUUID().toString().replace("-", "").take(12)}.$extension.enc"
            val outFile = File(vaultDir, fileName)
            outFile.writeBytes(encryptedBlob)

            var remotePath = ""
            if (uploadToCloudStorage) {
                remotePath = tryUploadToFirebaseStorage(userId, fileName, encryptedBlob)
            }

            EncryptedArtifactResult(
                localVaultPath = outFile.absolutePath,
                sha256Digest = digest,
                storagePath = remotePath,
            )
        }
    }

    suspend fun encryptAndStoreUri(
        sourceUri: Uri,
        extension: String,
        userId: String,
        uploadToCloudStorage: Boolean,
    ): Result<EncryptedArtifactResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected evidence file.")
            encryptAndStoreBytes(bytes, extension, userId, uploadToCloudStorage).getOrThrow()
        }
    }

    suspend fun decryptVaultFile(vaultPath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(vaultPath)
            require(file.exists()) { "Encrypted vault file not found." }
            decryptAesGcm(file.readBytes())
        }
    }

    private fun encryptAesGcm(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(4 + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decryptAesGcm(blob: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(blob)
        val ivLength = buffer.int
        require(ivLength in 12..16) { "Corrupted encryption header" }
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private suspend fun tryUploadToFirebaseStorage(
        userId: String,
        fileName: String,
        encryptedBlob: ByteArray,
    ): String {
        return try {
            val objectPath = "users/$userId/evidence/$fileName"
            val ref = Firebase.storage.reference.child(objectPath)
            ref.putBytes(encryptedBlob).await()
            objectPath
        } catch (_: Exception) {
            // Firebase Storage bucket requires manual activation in Firebase Console;
            // encrypted local vault artifact remains intact and accessible.
            ""
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "safeguard_evidence_aes256_gcm"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun sha256Hex(input: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun hashPin(pin: String): String = sha256Hex(pin.trim().toByteArray(Charsets.UTF_8))
    }
}
