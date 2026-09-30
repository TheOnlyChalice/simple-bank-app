package com.simplebank.api;

import com.jayway.jsonpath.JsonPath;
import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP layer for registration and /api/users, through the real security filters:
 * customers register themselves and manage only their own profile; listing users is ADMIN only.
 * Runs against MongoDB in Docker (see MongoTestBase), emptied before each test.
 */
class UserApiTest extends MongoTestBase {

    private MockMvc anonymous;

    @BeforeEach
    void setUp() {
        anonymous = anonymousMockMvc();
    }

    // ----- Registration -----

    @Test
    void registerReturns201WithLocationTokenAndProfile() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Jane Doe", "Jane@Example.com", "Secret123", TestData.ADDRESS_JSON)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/users/")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.userId").isNumber())
                .andExpect(jsonPath("$.user.email").value("jane@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.address.city").value("Baltimore"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void stateIsStoredUppercase() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Jane", "jane@example.com", "Secret123",
                                "{\"street\": \" 1 Oak Ave \", \"city\": \" Towson \", \"state\": \"md\", \"zip\": \"21204\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.address.state").value("MD"))
                .andExpect(jsonPath("$.user.address.city").value("Towson"))
                .andExpect(jsonPath("$.user.address.street").value("1 Oak Ave"));
    }

    @Test
    void invalidUserReturns400WithFieldErrors() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("", "not-an-email", "Secret123", TestData.ADDRESS_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"));
    }

    @Test
    void weakPasswordReturns400() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Jane", "jane@example.com", "password", TestData.ADDRESS_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password", containsString("at least one letter and one number")));
    }

    @Test
    void missingAddressReturns400() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Jane\", \"email\": \"jane@example.com\", \"password\": \"Secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.address").value("address is required"));
    }

    @Test
    void invalidStateAndZipReturn400WithFieldErrors() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Jane", "jane@example.com", "Secret123",
                                "{\"street\": \"1 Oak Ave\", \"city\": \"Towson\", \"state\": \"Maryland\", \"zip\": \"2120\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.state']").value("state must be a 2-letter code, e.g. MD"))
                .andExpect(jsonPath("$.fieldErrors['address.zip']", containsString("5 digits")));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        register("Jane", "jane@example.com");

        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Other Jane", "JANE@example.com", "Secret123", TestData.ADDRESS_JSON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("jane@example.com")));
    }

    // ----- A customer's own profile -----

    @Test
    void customerCanReadTheirOwnProfile() throws Exception {
        Registered jane = register("Jane", "jane@example.com");

        mockMvcAs(jane.token()).perform(get("/api/users/" + jane.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void customerCanUpdateTheirOwnProfile() throws Exception {
        Registered jane = register("Jane", "jane@example.com");

        mockMvcAs(jane.token()).perform(put("/api/users/" + jane.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Jane Smith", "jane@example.com",
                                "{\"street\": \"9 Elm St\", \"city\": \"Annapolis\", \"state\": \"MD\", \"zip\": \"21401\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Smith"))
                .andExpect(jsonPath("$.address.city").value("Annapolis"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void customerCanDeleteTheirOwnProfile() throws Exception {
        Registered jane = register("Jane", "jane@example.com");

        mockMvcAs(jane.token()).perform(delete("/api/users/" + jane.userId()))
                .andExpect(status().isNoContent());
        adminMockMvc().perform(get("/api/users/" + jane.userId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUserWithAccountsReturns409() throws Exception {
        Registered jane = register("Jane", "jane@example.com");
        MockMvc asJane = mockMvcAs(jane.token());
        asJane.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + jane.userId() + ", \"accountType\": \"SAVINGS\"}"))
                .andExpect(status().isCreated());

        asJane.perform(delete("/api/users/" + jane.userId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("still has accounts")));
    }

    // ----- Admin -----

    @Test
    void adminCanListUsersPaged() throws Exception {
        register("User 1", "user1@example.com");
        register("User 2", "user2@example.com");
        register("User 3", "user3@example.com");

        // The test admin is created after the three customers, so it's the 4th user
        adminMockMvc().perform(get("/api/users").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("User 1"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void unknownUserReturns404WithStandardErrorShape() throws Exception {
        adminMockMvc().perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User with id 999999 was not found"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidPageSizeReturns400() throws Exception {
        adminMockMvc().perform(get("/api/users").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("size")));
    }

    // ----- Malformed requests -----

    @Test
    void missingContentTypeReturns415() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .content(registerJson("Jane", "jane@example.com", "Secret123", TestData.ADDRESS_JSON)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Content-Type must be application/json"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        adminMockMvc().perform(get("/api/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("abc")));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        adminMockMvc().perform(patch("/api/users/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ----- Helpers -----

    private record Registered(long userId, String token) {
    }

    private Registered register(String name, String email) throws Exception {
        String json = anonymous.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(name, email, "Secret123", TestData.ADDRESS_JSON)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Registered(JsonPath.<Number>read(json, "$.user.userId").longValue(),
                JsonPath.<String>read(json, "$.accessToken"));
    }

    private static String registerJson(String name, String email, String password, String addressJson) {
        return "{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"password\": \"" + password
                + "\", \"address\": " + addressJson + "}";
    }

    private static String userJson(String name, String email, String addressJson) {
        return "{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"address\": " + addressJson + "}";
    }
}
