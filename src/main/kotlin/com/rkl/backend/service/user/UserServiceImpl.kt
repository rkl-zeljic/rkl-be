package com.rkl.backend.service.user

import com.rkl.backend.dao.user.UserDao
import com.rkl.backend.dto.user.CreateUserRequestDTO
import com.rkl.backend.dto.user.UpdateCurrentUserRequestDTO
import com.rkl.backend.dto.user.UpdateUserRequestDTO
import com.rkl.backend.dto.user.UserResponseDTO
import com.rkl.backend.enums.UserType
import com.rkl.backend.mapper.user.UserMapper
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.searchfilter.dto.UserFilter
import com.rkl.backend.service.AuthService
import com.rkl.backend.validation.user.UserValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserServiceImpl(
    private val userDao: UserDao,
    private val userMapper: UserMapper,
    private val userValidator: UserValidator,
    private val measurementService: com.rkl.backend.service.MeasurementService,
    private val otpremnicaRepository: OtpremnicaRepository,
    private val prevoznicaRepository: PrevoznicaRepository,
    private val authService: AuthService,
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

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN')")
    override fun create(createUserRequestDTO: CreateUserRequestDTO): UserResponseDTO {
        if (createUserRequestDTO.type == UserType.ADMIN) {
            userValidator.validateUserIsAdmin()
        }

        val newUser = userMapper.mapToEntity(createUserRequestDTO)
        if (!createUserRequestDTO.password.isNullOrBlank()) {
            newUser.passwordHash = authService.hashPassword(createUserRequestDTO.password)
        }
        val created = userDao.create(newUser)
        if (!created.driverName.isNullOrBlank()) {
            measurementService.relinkDriverMeasurements(created.id!!, null, created.driverName)
            relinkDocumentsToDriver(created.id!!, null, created.driverName)
        }
        return created
            .let(userMapper::mapToDTO)
            .also {
                log.info("User with ID ${it.id} has been created")
            }
    }

    @Transactional
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
            updateUserRequestDTO.username?.run { it.username = this }
            if (!updateUserRequestDTO.password.isNullOrBlank()) {
                it.passwordHash = authService.hashPassword(updateUserRequestDTO.password)
            }
        }

        val updated = userDao.update(userDB)
        if (updateUserRequestDTO.driverName != null && oldDriverName != updated.driverName) {
            measurementService.relinkDriverMeasurements(updated.id!!, oldDriverName, updated.driverName)
            relinkDocumentsToDriver(updated.id!!, oldDriverName, updated.driverName)
        }
        return updated
            .let(userMapper::mapToDTO)
            .also {
                log.info("User with ID ${it.id} has been updated")
            }
    }

    @Transactional
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

    @Transactional
    override fun createCurrentUserSignature(email: String, signature: String): UserResponseDTO {
        val userDB = userDao.findByName(email)
        if (userDB.signature != null) {
            throw IllegalStateException("Potpis već postoji. Koristite ažuriranje potpisa.")
        }
        userDB.signature = signature
        val updated = userDao.update(userDB)

        backfillDriverSignature(updated.id!!, signature)

        return updated
            .let(userMapper::mapToDTO)
            .also {
                log.info("Signature created for user with ID ${it.id}")
            }
    }

    @Transactional
    override fun updateCurrentUserSignature(email: String, signature: String): UserResponseDTO {
        val userDB = userDao.findByName(email)
        if (userDB.signature == null) {
            throw IllegalStateException("Potpis ne postoji. Prvo kreirajte potpis.")
        }
        userDB.signature = signature
        val updated = userDao.update(userDB)

        backfillDriverSignature(updated.id!!, signature)

        return updated
            .let(userMapper::mapToDTO)
            .also {
                log.info("Signature updated for user with ID ${it.id}")
            }
    }

    @Transactional
    override fun deleteCurrentUserSignature(email: String): UserResponseDTO {
        val userDB = userDao.findByName(email)
        userDB.signature = null
        return userDao.update(userDB)
            .let(userMapper::mapToDTO)
            .also {
                log.info("Signature deleted for user with ID ${it.id}")
            }
    }

    private fun backfillDriverSignature(userId: Long, signature: String) {
        val otpCount = otpremnicaRepository.backfillDriverSignature(userId, signature)
        val prevCount = prevoznicaRepository.backfillDriverSignature(userId, signature)
        if (otpCount > 0 || prevCount > 0) {
            log.info("Backfilled driver signature for user $userId: $otpCount otpremnice, $prevCount prevoznice")
        }
    }

    private fun relinkDocumentsToDriver(userId: Long, oldDriverName: String?, newDriverName: String?) {
        if (!oldDriverName.isNullOrBlank() && oldDriverName != newDriverName) {
            val unlinkedOtp = otpremnicaRepository.unlinkFromDriver(userId)
            val unlinkedPrev = prevoznicaRepository.unlinkFromDriver(userId)
            log.info("Unlinked $unlinkedOtp otpremnice and $unlinkedPrev prevoznice from user $userId (old driverName: $oldDriverName)")
        }
        if (!newDriverName.isNullOrBlank()) {
            val linkedOtp = otpremnicaRepository.linkToDriver(userId, newDriverName)
            val linkedPrev = prevoznicaRepository.linkToDriver(userId, newDriverName)
            log.info("Linked $linkedOtp otpremnice and $linkedPrev prevoznice to user $userId (driverName: $newDriverName)")
        }
    }

    override fun getCurrentUserOrCreateIfNotExist(name: String, isAdmin: Boolean): UserResponseDTO {
        userDao.createUserIfNotExists(name, isAdmin)
        return userDao.findByName(name).let(userMapper::mapToDTO)
    }
}
