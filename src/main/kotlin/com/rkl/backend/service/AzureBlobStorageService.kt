package com.rkl.backend.service

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.models.BlobStorageException
import com.rkl.backend.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

@Service
class AzureBlobStorageService(private val appProperties: AppProperties) {

    private val logger = LoggerFactory.getLogger(AzureBlobStorageService::class.java)

    private val containerClient: BlobContainerClient by lazy {
        val storage = appProperties.azure.storage
        val client = BlobContainerClientBuilder()
            .connectionString(storage.connectionString)
            .containerName(storage.containerName)
            .buildClient()

        if (!client.exists()) {
            client.create()
            logger.info("Created Azure Blob container: ${storage.containerName}")
        }

        client
    }

    fun upload(inputStream: InputStream, size: Long, originalFilename: String): String {
        val extension = originalFilename.substringAfterLast(".", "")
        val blobName = "${UUID.randomUUID()}.$extension"

        val blobClient = containerClient.getBlobClient(blobName)
        blobClient.upload(inputStream, size, true)
        logger.info("Uploaded blob: $blobName (original: $originalFilename, size: $size)")

        return blobName
    }

    fun download(blobName: String): ByteArray {
        val blobClient = containerClient.getBlobClient(blobName)
        val outputStream = ByteArrayOutputStream()
        blobClient.downloadStream(outputStream)
        return outputStream.toByteArray()
    }

    fun delete(blobName: String) {
        try {
            val blobClient = containerClient.getBlobClient(blobName)
            blobClient.delete()
            logger.info("Deleted blob: $blobName")
        } catch (e: BlobStorageException) {
            logger.warn("Failed to delete blob $blobName: ${e.message}")
        }
    }
}
