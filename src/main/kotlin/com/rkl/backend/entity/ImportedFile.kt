package com.rkl.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "imported_files")
class ImportedFile(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "original_filename", nullable = false)
    var originalFilename: String = "",

    @Column(name = "blob_name", nullable = false, unique = true)
    var blobName: String = "",

    @Column(name = "file_size")
    var fileSize: Long = 0,

    @Column(name = "content_type")
    var contentType: String? = null,

    @Column(name = "uploaded_by")
    var uploadedBy: String? = null,

    @Column(name = "record_count")
    var recordCount: Int = 0,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "importedFile", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var merenja: MutableList<Merenje> = mutableListOf()
) {

    @PrePersist
    fun onPrePersist() {
        createdAt = LocalDateTime.now()
    }
}
