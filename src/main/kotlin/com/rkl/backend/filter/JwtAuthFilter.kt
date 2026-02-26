package com.rkl.backend.filter

import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import com.rkl.backend.config.RklConfig
import com.rkl.backend.entity.JwtKeyCache
import com.rkl.backend.repository.JwtKeyCacheRepository
import com.rkl.backend.service.user.UserService
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import kotlin.text.startsWith
import kotlin.text.substring

@Order(3)
class JwtAuthFilter(
    private val securityConfig: RklConfig.Security,
    private val userService: UserService,
    private val jwtKeyCacheRepository: JwtKeyCacheRepository,
) : GenericFilterBean() {

    private val log = LoggerFactory.getLogger(JwtAuthFilter::class.java)
    private val httpClient = HttpClient.newHttpClient()
    private val cachedVerifier = AtomicReference<JWSVerifier?>(null)
    private val cachedKeyJson = AtomicReference<String?>(null)

    private val configuredVerifier: JWSVerifier? by lazy {
        try {
            val ecKey = ECKey.parse(securityConfig.jwtPublicKey)
            ECDSAVerifier(ecKey)
        } catch (e: Exception) {
            log.warn("Failed to parse configured JWT public key: ${e.message}")
            null
        }
    }

    private fun loadFromDatabase(): JWSVerifier? {
        return try {
            val dbKey = jwtKeyCacheRepository.findById(1L).orElse(null)
            if (dbKey != null) {
                log.info("Loading JWT public key from database")
                val ecKey = ECKey.parse(dbKey.publicKeyJson)
                cachedKeyJson.set(dbKey.publicKeyJson)
                ECDSAVerifier(ecKey)
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to load JWT key from database: ${e.message}")
            null
        }
    }

    private fun saveToDatabase(keyJson: String) {
        try {
            val cache = JwtKeyCache(
                id = 1L,
                publicKeyJson = keyJson,
                fetchedAt = Instant.now()
            )
            jwtKeyCacheRepository.save(cache)
            cachedKeyJson.set(keyJson)
            log.info("Saved JWT public key to database cache")
        } catch (e: Exception) {
            log.error("Failed to save JWT key to database: ${e.message}")
        }
    }

    private fun getVerifier(): JWSVerifier? {
        // 1. Check in-memory cache
        cachedVerifier.get()?.let { return it }

        // 2. Try a configured key
        configuredVerifier?.let {
            cachedVerifier.set(it)
            return it
        }

        // 3. Try loading from a database
        loadFromDatabase()?.let {
            cachedVerifier.set(it)
            return it
        }

        return null
    }

    private fun fetchAndCacheVerifierFromJwks(): JWSVerifier? {
        return try {
            log.info("Fetching new key from JWKS endpoint")
            val request = HttpRequest.newBuilder()
                .uri(URI.create(securityConfig.jwksUrl))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val jwkSet = JWKSet.parse(response.body())
                val ecKey = jwkSet.keys.firstOrNull { it is ECKey } as? ECKey
                if (ecKey != null) {
                    val keyJson = ecKey.toJSONString()
                    val newVerifier = ECDSAVerifier(ecKey)
                    cachedVerifier.set(newVerifier)
                    saveToDatabase(keyJson)
                    log.info("Successfully fetched and cached new key from JWKS")
                    newVerifier
                } else {
                    log.error("No EC key found in JWKS response")
                    null
                }
            } else {
                log.error("Failed to fetch JWKS: HTTP ${response.statusCode()}")
                null
            }
        } catch (e: Exception) {
            log.error("Error fetching JWKS: ${e.message}")
            null
        }
    }

    private fun verifyToken(signedJWT: SignedJWT): Boolean {
        val currentVerifier = getVerifier()

        // If no valid configured key, fetch from JWKS immediately
        if (currentVerifier == null) {
            log.warn("No valid configured key, fetching from JWKS")
            val newVerifier = fetchAndCacheVerifierFromJwks()
            return newVerifier?.let { signedJWT.verify(it) } ?: false
        }

        // Try with the current verifier (cached or configured)
        if (signedJWT.verify(currentVerifier)) {
            return true
        }

        // If failed, try fetching a new key from JWKS
        log.warn("Signature verification failed with current key, attempting JWKS refresh")
        val newVerifier = fetchAndCacheVerifierFromJwks()
        return newVerifier?.let { signedJWT.verify(it) } ?: false
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val authHeader = (request as HttpServletRequest).getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)

            try {
                val signedJWT = SignedJWT.parse(token)

                if (!verifyToken(signedJWT)) {
                    throw RuntimeException("Invalid JWT signature")
                }

                val claims = signedJWT.jwtClaimsSet

                if (claims.issuer != securityConfig.expectedIssuer) {
                    throw RuntimeException("Invalid issuer")
                }

                if (claims.expirationTime?.before(Date()) == true) {
                    throw RuntimeException("Token expired")
                }

                val email = claims.getClaim("email") as? String
                    ?: throw RuntimeException("Email claim not found")

                val isAdmin = securityConfig.admins.contains(email)
                val userResponseDTO = if (isAdmin) {
                    userService.getCurrentUserOrCreateIfNotExist(email, true)
                } else {
                    try {
                        userService.getCurrentUser(email)
                    } catch (e: Exception) {
                        val httpResponse = response as HttpServletResponse
                        httpResponse.status = HttpServletResponse.SC_FORBIDDEN
                        httpResponse.contentType = "application/json"
                        httpResponse.characterEncoding = "UTF-8"
                        httpResponse.writer.apply {
                            write("""{"message": "User not registered. Contact an admin."}""")
                            flush()
                        }
                        return
                    }
                }

                val authorities = listOf(SimpleGrantedAuthority("ROLE_${userResponseDTO.type}"))

                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(email, null, authorities)

            } catch (e: Exception) {
                val httpResponse = response as HttpServletResponse
                httpResponse.status = HttpServletResponse.SC_UNAUTHORIZED
                httpResponse.contentType = "application/json"
                httpResponse.characterEncoding = "UTF-8"

                val writer = httpResponse.writer
                val json = """{"message": "${e.message}"}"""
                writer.write(json)
                writer.flush()
                return
            }
        }

        chain.doFilter(request, response)
    }
}