package com.rkl.backend.dao.user

import com.rkl.backend.entity.RklUser
import com.rkl.backend.enums.UserType
import com.rkl.backend.exception.ErrorCode.Companion.ENTITY_ALREADY_EXIST_ERROR
import com.rkl.backend.exception.ErrorCode.Companion.ENTITY_DOES_NOT_EXIST_ERROR
import com.rkl.backend.exception.base.NotExistsException
import com.rkl.backend.repository.MerenjeRepository
import com.rkl.backend.repository.OtpremnicaRepository
import com.rkl.backend.repository.PrevoznicaRepository
import com.rkl.backend.repository.UserRepository
import com.rkl.backend.searchfilter.criteria.builder.UserFilterCriteriaBuilder
import com.rkl.backend.searchfilter.dto.UserFilter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrElse

@Service
class UserDaoImpl(
    private val userRepository: UserRepository,
    private val userFilterCriteriaBuilder: UserFilterCriteriaBuilder,
    private val prevoznicaRepository: PrevoznicaRepository,
    private val otpremnicaRepository: OtpremnicaRepository,
    private val merenjeRepository: MerenjeRepository,
) : UserDao {
    override fun findAll(pageable: Pageable, userFilter: UserFilter): Page<RklUser> {
        return userRepository.findAll(userFilterCriteriaBuilder.buildQuery(userFilter), pageable)
    }

    override fun findById(id: Long): RklUser {
        return userRepository.findById(id).getOrElse {
            throw NotExistsException(ENTITY_DOES_NOT_EXIST_ERROR, "User with id[$id] doesn't exist!")
        }
    }

    override fun findByName(name: String): RklUser {
        return userRepository.findByEmail(name)
            ?: userRepository.findByUsername(name)
            ?: throw NotExistsException(ENTITY_DOES_NOT_EXIST_ERROR, "User with identifier[$name] doesn't exist!")
    }

    override fun create(rklUser: RklUser): RklUser {
        rklUser.email?.takeIf { it.isNotBlank() }?.let { assertDoesNotExistByEmail(it) }

        return userRepository.save(rklUser)
    }

    override fun update(rklUser: RklUser): RklUser {
        assertExistsById(rklUser.id!!)
        return userRepository.save(rklUser)
    }

    override fun deleteById(id: Long) {
        assertExistsById(id)
        prevoznicaRepository.unlinkFromUser(id)
        otpremnicaRepository.unlinkFromUser(id)
        merenjeRepository.unlinkMeasurementsFromDriver(id)
        userRepository.deleteById(id)
    }

    override fun createUserIfNotExists(email: String, isAdmin: Boolean) {
        val existingUser = userRepository.findByEmail(email)
        if (existingUser != null) {
            if (isAdmin && existingUser.type != UserType.ADMIN) {
                existingUser.type = UserType.ADMIN
                userRepository.save(existingUser)
            }
            return
        }
        try {
            create(
                RklUser(email = email, type = if (isAdmin) UserType.ADMIN else UserType.DRIVER)
            )
        } catch (_: DataIntegrityViolationException) {
            // concurrent request already created this user — safe to ignore
        }
    }

    private fun assertDoesNotExistByEmail(email: String) {
        userRepository.findByEmail(email)
            ?.let { throw NotExistsException(ENTITY_ALREADY_EXIST_ERROR, "User with name[$email] already exist!") }
    }

    private fun assertExistsById(id: Long) {
        userRepository.findById(id).getOrElse {
            throw NotExistsException(ENTITY_DOES_NOT_EXIST_ERROR, "User with id[$id] doesn't exist!")
        }
    }

    private fun generateFriendlyAlias(): String {
        val adjectives = listOf(
            "bright", "kind", "brave", "open", "gentle",
            "clear", "calm", "wise", "fair", "true",
            "hopeful", "peaceful", "warm", "honest", "friendly",
            "strong", "joyful", "caring", "free", "steady",
            "happy", "lively", "clever", "bold", "graceful",
            "merry", "radiant", "cheerful", "loyal", "humble",
            "curious", "daring", "vivid", "witty", "playful",
            "faithful", "glorious", "spirited", "noble", "devoted"
        )

        val nouns = listOf(
            "freedom", "harmony", "unity", "hope", "justice",
            "equality", "vision", "peace", "voice", "courage",
            "trust", "kindness", "dream", "bridge", "light",
            "wisdom", "choice", "future", "truth", "path",
            "glory", "joy", "valor", "faith", "honor",
            "spirit", "strength", "serenity", "virtue", "merit",
            "clarity", "grace", "fortune", "happiness", "liberty",
            "radiance", "soul", "guidance", "guardian", "protector"
        )

        val adjective = adjectives.random()
        val noun = nouns.random()
        val number = (100..999).random()

        // Can combine 1,440,000 unique aliases
        return "$adjective-$noun-$number"
    }
}