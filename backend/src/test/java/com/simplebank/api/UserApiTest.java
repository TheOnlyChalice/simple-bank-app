package com.simplebank.api;

import com.jayway.jsonpath.JsonPath;
import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
 * Tests the HTTP layer for /api/users: URLs, status codes, headers, JSON, validation,
 * and the error format. MockMvc sends requests straight into Spring without a real server.
 * Runs against MongoDB in Docker (see MongoTestBase), emptied before each test.
 */
class UserApiTest extends MongoTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ----- Create -----

    @Test
    void createUserReturns201WithLocationAndBody() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Jane Doe", "Jane@Example.com", TestData.ADDRESS_JSON)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/users/")))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.address.city").value("Baltimore"))
                .andExpect(jsonPath("$.address.zip").value("21201"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void stateIsStoredUppercase() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Jane", "jane@example.com",
                                "{\"street\": \" 1 Oak Ave \", \"city\": \" Towson \", \"state\": \"md\", \"zip\": \"21204\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.state").value("MD"))
                .andExpect(jsonPath("$.address.city").value("Towson"))
                .andExpect(jsonPath("$.address.street").value("1 Oak Ave"));
    }

    @Test
    void invalidUserReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("", "not-an-email", TestData.ADDRESS_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"));
    }

    @Test
    void missingAddressReturns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Jane\", \"email\": \"jane@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.address").value("address is required"));
    }

    @Test
    void invalidStateAndZipReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Jane", "jane@example.com",
                                "{\"street\": \"1 Oak Ave\", \"city\": \"Towson\", \"state\": \"Maryland\", \"zip\": \"2120\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.state']").value("state must be a 2-letter code, e.g. MD"))
                .andExpect(jsonPath("$.fieldErrors['address.zip']", containsString("5 digits")));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        createUser("Jane", "jane@example.com");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Other Jane", "JANE@example.com", TestData.ADDRESS_JSON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("jane@example.com")));
    }

    // ----- Read -----

    @Test
    void unknownUserReturns404WithStandardErrorShape() throws Exception {
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User with id 999999 was not found"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void listUsersIsPaged() throws Exception {
        createUser("User 1", "user1@example.com");
        createUser("User 2", "user2@example.com");
        createUser("User 3", "user3@example.com");

        mockMvc.perform(get("/api/users").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("User 1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void invalidPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/users").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("size")));
    }

    // ----- Update / Delete -----

    @Test
    void updateUserReturns200() throws Exception {
        long id = createUser("Jane", "jane@example.com");

        mockMvc.perform(put("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Jane Smith", "jane@example.com",
                                "{\"street\": \"9 Elm St\", \"city\": \"Annapolis\", \"state\": \"MD\", \"zip\": \"21401\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Smith"))
                .andExpect(jsonPath("$.address.city").value("Annapolis"));
    }

    @Test
    void deleteUserReturns204ThenUserIsGone() throws Exception {
        long id = createUser("Jane", "jane@example.com");

        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUserWithAccountsReturns409() throws Exception {
        long id = createUser("Jane", "jane@example.com");
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + id + ", \"accountType\": \"SAVINGS\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("still has accounts")));
    }

    // ----- Malformed requests -----

    @Test
    void missingContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/users")
                        .content(userJson("Jane", "jane@example.com", TestData.ADDRESS_JSON)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Content-Type must be application/json"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("abc")));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/users/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ----- Helpers -----

    private static String userJson(String name, String email, String addressJson) {
        return "{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"address\": " + addressJson + "}";
    }

    private long createUser(String name, String email) throws Exception {
        String json = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(name, email, TestData.ADDRESS_JSON)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json, "$.userId").longValue();
    }
}
