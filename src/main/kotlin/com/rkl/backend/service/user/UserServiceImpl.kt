package com.rkl.backend.service.user

import com.rkl.backend.dao.user.UserDao
import com.rkl.backend.dto.user.CreateUserRequestDTO
import com.rkl.backend.dto.user.UpdateCurrentUserRequestDTO
import com.rkl.backend.dto.user.UpdateUserRequestDTO
import com.rkl.backend.dto.user.UserResponseDTO
import com.rkl.backend.enums.UserType
import com.rkl.backend.mapper.user.UserMapper
import com.rkl.backend.searchfilter.dto.UserFilter
import com.rkl.backend.validation.user.UserValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class UserServiceImpl(
    private val userDao: UserDao,
    private val userMapper: UserMapper,
    private val userValidator: UserValidator,
    private val measurementService: com.rkl.backend.service.MeasurementService,
) : UserService {

    val log: Logger = LoggerFactory.getLogger(this.javaClass)

    @PreAuthorize("hasAnyRole('ADMIN')")
    override fun findAll(pageable: Pageable, userFilter: UserFilter): Page<UserResponseDTO> {
        return userDao.findAll(pageable, userFilter).map(userMapper::mapToDTO)
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    override fun findById(id: Long): UserResponseDTO {
        return userDao.findById(id).let(userMapper::mapToDTO)
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    override fun create(createUserRequestDTO: CreateUserRequestDTO): UserResponseDTO {
        if (createUserRequestDTO.type == UserType.ADMIN) {
            userValidator.validateUserIsAdmin()
        }

        val newUser = userMapper.mapToEntity(createUserRequestDTO)
        val created = userDao.create(newUser)
        if (!created.driverName.isNullOrBlank()) {
            measurementService.relinkDriverMeasurements(created.id!!, null, created.driverName)
        }
        return created
            .let(userMapper::mapToDTO)
            .also {
                log.info("User with ID ${it.id} has been created")
            }
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    override fun update(updateUserRequestDTO: UpdateUserRequestDTO): UserResponseDTO {
        val userDB = userDao.findById(updateUserRequestDTO.id!!)

        if (updateUserRequestDTO.type != null && (updateUserRequestDTO.type == UserType.ADMIN || userDB.type == UserType.ADMIN)) {
            userValidator.validateUserIsAdmin()
        }

        val oldDriverName = userDB.driverName
        userDB.also {
            updateUserRequestDTO.email?.run { it.email = this }
            updateUserRequestDTO.type?.run { it.type = this }
            updateUserRequestDTO.driverName?.run { it.driverName = this }
        }

        val updated = userDao.update(userDB)
        if (updateUserRequestDTO.driverName != null && oldDriverName != updated.driverName) {
            measurementService.relinkDriverMeasurements(updated.id!!, oldDriverName, updated.driverName)
        }
        return updated
            .let(userMapper::mapToDTO)
            .also {
                log.info("User with ID ${it.id} has been updated")
            }
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    override fun delete(id: Long) {
        userDao.deleteById(id)
            .also {
                log.info("User with ID $id has been deleted")
            }
    }

    override fun getCurrentUser(name: String): UserResponseDTO {
        return userDao.findByName(name).let(userMapper::mapToDTO)
    }

    override fun updateCurrentUser(
        name: String,
        updateCurrentUserRequestDTO: UpdateCurrentUserRequestDTO
    ): UserResponseDTO {
        val userDB = userDao.findByName(name)

        return userDao.update(userDB)
            .let(userMapper::mapToDTO)
            .also {
                log.info("Current user with ID ${it.id} has been updated")
            }
    }

    override fun updateCurrentUserSignature(email: String, signature: String?): UserResponseDTO {
        val userDB = userDao.findByName(email)
        userDB.signature = signature
        return userDao.update(userDB)
            .let(userMapper::mapToDTO)
            .also {
                log.info("Signature updated for user with ID ${it.id}")
            }
    }

    override fun getCurrentUserOrCreateIfNotExist(name: String, isAdmin: Boolean): UserResponseDTO {
        userDao.createUserIfNotExists(name, isAdmin)
        return userDao.findByName(name).let(userMapper::mapToDTO)
    }
}
