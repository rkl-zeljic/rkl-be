package com.rkl.backend.service

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.rkl.backend.config.RklConfig
import com.rkl.backend.dto.auth.LoginRequestDTO
import com.rkl.backend.dto.auth.LoginResponseDTO
import com.rkl.backend.repository.UserRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.Date

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val securityConfig: RklConfig.Security,
) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val signer = MACSigner(securityConfig.jwtSecret.toByteArray().copyOf(32))

    fun login(request: LoginRequestDTO): LoginResponseDTO {
        val user = userRepository.findByUsername(request.username)
            ?: throw IllegalArgumentException("Pogrešno korisničko ime ili lozinka")

        if (user.passwordHash == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Pogrešno korisničko ime ili lozinka")
        }

        val identifier = user.email ?: user.username
            ?: throw IllegalStateException("User has neither email nor username")

        val now = Date()
        val expiration = Date(now.time + 24 * 60 * 60 * 1000) // 24h

        val claims = JWTClaimsSet.Builder()
            .subject(identifier)
            .issuer(securityConfig.internalIssuer)
            .claim("identifier", identifier)
            .claim("username", user.username)
            .issueTime(now)
            .expirationTime(expiration)
            .build()

        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        signedJWT.sign(signer)

        return LoginResponseDTO(
            token = signedJWT.serialize(),
            email = user.email ?: "",
            type = user.type,
            driverName = user.driverName,
            username = user.username,
        )
    }

    fun hashPassword(rawPassword: String): String {
        return passwordEncoder.encode(rawPassword)!!
    }
}
