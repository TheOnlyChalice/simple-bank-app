package com.simplebank.security;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the security rules hold, through the real security filters:
 *   401 - no token, or a token that is tampered with, expired, or signed with another key
 *   403 - a customer touching someone else's profile or accounts, or an admin-only endpoint
 * and that logins and access denials are recorded in the audit log.
 *
 * Ana and Ben are customers. Ana has an account with $100.
 */
class SecurityTest extends MongoTestBase {

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc anonymous;
    private Registered ana;
    private Registered ben;
    private long anaAccount;

    @BeforeEach
    void setUp() throws Exception {
        anonymous = anonymousMockMvc();
        ana = register("Ana", "ana@example.com");
        ben = register("Ben", "ben@example.com");
        anaAccount = openAccount(ana);
        mockMvcAs(ana.token()).perform(post("/api/accounts/" + anaAccount + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isOk());
    }

    // ----- 401: not logged in -----

    @Test
    void noTokenIs401WithAHelpfulMessage() throws Exception {
        anonymous.perform(get("/api/users/" + ana.userId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        mockMvcAs(ana.token() + "x").perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("invalid or has expired")));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
        String expired = sign(jwtEncoder, ana.userId(), "ana@example.com", "CUSTOMER",
                tenMinutesAgo, tenMinutesAgo.plus(Duration.ofMinutes(5)));

        mockMvcAs(expired).perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIs401() throws Exception {
        JwtEncoder attackerEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "an-attackers-own-key-that-is-long-enough".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        // Claims to be an admin, but isn't signed with the server's key
        String forged = sign(attackerEncoder, ana.userId(), "ana@example.com", "ADMIN",
                Instant.now(), Instant.now().plus(Duration.ofHours(1)));

        mockMvcAs(forged).perform(get("/api/audit"))
                .andExpect(status().isUnauthorized());
    }

    // ----- Login -----

    @Test
    void loginWithTheRightPasswordReturnsAToken() throws Exception {
        anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ANA@example.com\", \"password\": \"Secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.userId").value(ana.userId()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@example.com\", \"password\": \"WrongGuess1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.com\", \"password\": \"Secret123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void registeringCannotMakeYouAnAdmin() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mallory\", \"email\": \"mallory@example.com\", \"password\": \"Secret123\", "
                                + "\"role\": \"ADMIN\", \"address\": " + TestData.ADDRESS_JSON + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        mockMvcAs(ben.token()).perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ben@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ----- 403: someone else's things -----

    @Test
    void customerCannotSeeOrChangeAnotherCustomersProfile() throws Exception {
        MockMvc asBen = mockMvcAs(ben.token());

        asBen.perform(get("/api/users/" + ana.userId()))
                .andExpect(status().isForbidden());
        asBen.perform(get("/api/users/" + ana.userId() + "/accounts"))
                .andExpect(status().isForbidden());
        asBen.perform(put("/api/users/" + ana.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hacked\", \"email\": \"ana@example.com\", \"address\": "
                                + TestData.ADDRESS_JSON + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotTouchAnotherCustomersAccount() throws Exception {
        MockMvc asBen = mockMvcAs(ben.token());

        asBen.perform(get("/api/accounts/" + anaAccount))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only access your own accounts"));
        asBen.perform(get("/api/accounts/" + anaAccount + "/transactions"))
                .andExpect(status().isForbidden());
        asBen.perform(post("/api/accounts/" + anaAccount + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50}"))
                .andExpect(status().isForbidden());

        assertThat(balanceOf(ana, anaAccount)).isEqualByComparingTo("100.00"); // nothing was taken
    }

    @Test
    void customerCannotOpenAnAccountForSomeoneElse() throws Exception {
        mockMvcAs(ben.token()).perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + ana.userId() + ", \"accountType\": \"SAVINGS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transfersGoFromYourOwnAccountToAnyone() throws Exception {
        long benAccount = openAccount(ben);

        // Ana can send money from her account to Ben's
        mockMvcAs(ana.token()).perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(anaAccount, benAccount, "40")))
                .andExpect(status().isOk());

        // Ben can't pull money out of Ana's account
        mockMvcAs(ben.token()).perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(anaAccount, benAccount, "10")))
                .andExpect(status().isForbidden());

        assertThat(balanceOf(ana, anaAccount)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(ben, benAccount)).isEqualByComparingTo("40.00");
    }

    @Test
    void customersCannotUseStaffEndpoints() throws Exception {
        MockMvc asAna = mockMvcAs(ana.token());

        asAna.perform(get("/api/users")).andExpect(status().isForbidden());
        asAna.perform(get("/api/accounts")).andExpect(status().isForbidden());
        asAna.perform(get("/api/accounts/premium").param("threshold", "1")).andExpect(status().isForbidden());
        asAna.perform(get("/api/audit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("bank staff")));
    }

    @Test
    void adminCanReachEverything() throws Exception {
        MockMvc asAdmin = adminMockMvc();

        asAdmin.perform(get("/api/users/" + ana.userId())).andExpect(status().isOk());
        asAdmin.perform(get("/api/accounts/" + anaAccount)).andExpect(status().isOk());
        asAdmin.perform(get("/api/users")).andExpect(status().isOk());
        asAdmin.perform(get("/api/audit")).andExpect(status().isOk());
    }

    // ----- The audit trail -----

    @Test
    void accessDenialsAreAuditedWithWhoTriedWhat() throws Exception {
        mockMvcAs(ben.token()).perform(post("/api/accounts/" + anaAccount + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50}"))
                .andExpect(status().isForbidden());

        adminMockMvc().perform(get("/api/audit").param("action", "ACCESS_DENIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].actor", containsString("ben@example.com")))
                .andExpect(jsonPath("$.content[0].accountId").value(anaAccount))
                .andExpect(jsonPath("$.content[0].details", containsString("/withdraw")));
    }

    @Test
    void loginsAreAudited() throws Exception {
        anonymous.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@example.com\", \"password\": \"WrongGuess1\"}"))
                .andExpect(status().isUnauthorized());

        adminMockMvc().perform(get("/api/audit").param("action", "LOGIN").param("outcome", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("Invalid email or password"))
                .andExpect(jsonPath("$.content[0].details", containsString("ana@example.com")));
    }

    @Test
    void actionsAreAuditedUnderTheLoggedInUser() throws Exception {
        adminMockMvc().perform(get("/api/audit")
                        .param("accountId", String.valueOf(anaAccount))
                        .param("action", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actor").value("ana@example.com (user " + ana.userId() + ", CUSTOMER)"));
    }

    // ----- Helpers -----

    private record Registered(long userId, String token) {
    }

    private Registered register(String name, String email) throws Exception {
        String json = anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"password\": \"Secret123\", "
                                + "\"address\": " + TestData.ADDRESS_JSON + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Registered(JsonPath.<Number>read(json, "$.user.userId").longValue(),
                JsonPath.<String>read(json, "$.accessToken"));
    }

    private long openAccount(Registered owner) throws Exception {
        String json = mockMvcAs(owner.token()).perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + owner.userId() + ", \"accountType\": \"SAVINGS\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json, "$.accountId").longValue();
    }

    private BigDecimal balanceOf(Registered owner, long accountId) throws Exception {
        String json = mockMvcAs(owner.token()).perform(get("/api/accounts/" + accountId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.read(json, "$.balance").toString());
    }

    private static String transferJson(long from, long to, String amount) {
        return "{\"fromAccountId\": " + from + ", \"toAccountId\": " + to + ", \"amount\": " + amount + "}";
    }

    /** Signs a token with the given encoder, for building expired or forged tokens. */
    private static String sign(JwtEncoder encoder, long userId, String email, String role,
                               Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("simple-bank")
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", email)
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
