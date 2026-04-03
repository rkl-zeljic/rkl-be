package com.rkl.backend.repository

import com.rkl.backend.entity.RklUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<RklUser, Long>, JpaSpecificationExecutor<RklUser> {
    fun findByEmail(email: String): RklUser?
    fun findByUsername(username: String): RklUser?
    fun findByDriverNameIgnoreCase(driverName: String): RklUser?
}