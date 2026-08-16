package com.epam.gymcrm.config;

import com.epam.gymcrm.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void traineeRegistrationShouldBePublic() throws Exception {
        mockMvc.perform(post("/api/trainees/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Security",
                                  "lastName": "Test"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void trainerRegistrationShouldBePublic() throws Exception {
        mockMvc.perform(post("/api/trainers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Security",
                                  "lastName": "Trainer"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginShouldBePublic() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trainingTypesShouldRejectAnonymousRequest()
            throws Exception {

        mockMvc.perform(get("/api/training-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Transaction-Id"));
    }

    @Test
    void allowedOriginShouldReceiveCorsHeaders()
            throws Exception {

        mockMvc.perform(options("/api/training-types")
                        .header(
                                "Origin",
                                "http://localhost:3000"
                        )
                        .header(
                                "Access-Control-Request-Method",
                                "GET"
                        )
                        .header(
                                "Access-Control-Request-Headers",
                                "Authorization"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        containsString("GET")
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("Authorization")
                ));
    }

    @Test
    void unknownOriginShouldBeRejected()
            throws Exception {

        mockMvc.perform(options("/api/training-types")
                        .header(
                                "Origin",
                                "https://untrusted.example"
                        )
                        .header(
                                "Access-Control-Request-Method",
                                "GET"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Origin"
                ));
    }

    @Test
    void passwordEncoderShouldHashAndSaltPasswords() {
        String firstHash =
                passwordEncoder.encode("samePassword");

        String secondHash =
                passwordEncoder.encode("samePassword");

        assertNotEquals("samePassword", firstHash);
        assertNotEquals(firstHash, secondHash);

        assertTrue(
                passwordEncoder.matches(
                        "samePassword",
                        firstHash
                )
        );

        assertTrue(
                passwordEncoder.matches(
                        "samePassword",
                        secondHash
                )
        );
    }

    @Test
    void generatedJwtShouldContainRequiredClaims() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "John.Smith",
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_USER"
                                )
                        )
                );

        String token =
                jwtService.generateToken(authentication);

        Jwt decodedToken =
                jwtDecoder.decode(token);

        assertEquals(
                "John.Smith",
                decodedToken.getSubject()
        );

        assertEquals(
                "gym-crm",
                decodedToken.getClaimAsString("iss")
        );

        assertNotNull(decodedToken.getId());
        assertNotNull(decodedToken.getIssuedAt());
        assertNotNull(decodedToken.getExpiresAt());
        assertTrue(
                decodedToken.getAudience()
                        .contains("trainer-workload-service")
        );

        assertTrue(
                decodedToken.getExpiresAt()
                        .isAfter(
                                decodedToken.getIssuedAt()
                        )
        );
    }

}
