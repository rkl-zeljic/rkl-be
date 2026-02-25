package com.rkl.backend.dao.user

import com.rkl.backend.entity.RklUser
import com.rkl.backend.searchfilter.dto.UserFilter
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserDao {
    fun findAll(pageable: Pageable, userFilter: UserFilter): Page<RklUser>
    fun findById(id: Long): RklUser
    fun findByName(name: String): RklUser
    fun create(rklUser: RklUser): RklUser
    fun update(rklUser: RklUser): RklUser
    fun deleteById(id: Long)
    fun createUserIfNotExists(email: String)
}