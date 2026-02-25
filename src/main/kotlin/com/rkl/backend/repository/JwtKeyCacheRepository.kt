package com.rkl.backend.repository

import com.rkl.backend.entity.JwtKeyCache
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JwtKeyCacheRepository : JpaRepository<JwtKeyCache, Long>