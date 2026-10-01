package com.simplebank;

import com.simplebank.model.Address;
import com.simplebank.model.Role;
import com.simplebank.model.User;
import com.simplebank.repository.UserRepository;
import com.simplebank.security.TokenService;
import com.simplebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base class for every Spring test. Starts one MongoDB 8.0 in Docker for the whole
 * test run and points the app at it. The app's startup code then applies the same
 * validation rules and indexes as on Atlas.
 *
 * MongoDB only supports transactions on a replica set (every Atlas cluster is one),
 * so the container is started as a single-member replica set named "rs0".
 *
 * Before each test, the collections are emptied (their rules and indexes are kept),
 * so every test starts from a clean database. The audit log is emptied too; that is
 * only done in tests, since the app itself never deletes audit events.
 *
 * For API tests, the helpers below build MockMvc instances that go through the real
 * Spring Security filters, sending requests with no token, as a given user, or as an admin.
 */
@SpringBootTest
public abstract class MongoTestBase {

    private static final int MONGO_PORT = 27017;

    /** The test admin. Its address is outside every search test's filters. */
    protected static final String ADMIN_EMAIL = "admin@test.example.com";
    private static final Address ADMIN_ADDRESS = new Address("1 Bank Plaza", "Washington", "DC", "20001");

    static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:8.0")
            .withExposedPorts(MONGO_PORT)
            .withCommand("--replSet", "rs0", "--bind_ip_all")
            .waitingFor(Wait.forListeningPort());

    static {
        MONGO.start(); // started once, shared by all test classes, removed when the tests finish
        initializeReplicaSet();
    }

    /** Turns the server into a one-member replica set, then waits until it can accept writes. */
    private static void initializeReplicaSet() {
        try {
            MONGO.execInContainer("mongosh", "--quiet", "--eval",
                    "rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'localhost:27017' }] })");

            for (int attempt = 0; attempt < 60; attempt++) {
                String isPrimary = MONGO.execInContainer("mongosh", "--quiet", "--eval",
                        "db.hello().isWritablePrimary").getStdout().trim();
                if (isPrimary.equals("true")) {
                    return;
                }
                Thread.sleep(500);
            }
            throw new IllegalStateException("MongoDB replica set did not become ready in time");
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize the MongoDB replica set", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while initializing the MongoDB replica set", e);
        }
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        // directConnection=true: talk to this one server through the port Docker mapped,
        // instead of the replica set's internal address (localhost:27017 inside the container)
        registry.add("spring.mongodb.uri", () -> "mongodb://" + MONGO.getHost() + ":"
                + MONGO.getMappedPort(MONGO_PORT) + "/simple_bank?directConnection=true");
    }

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository usersForTests;

    @Autowired
    private UserService userServiceForTests;

    @BeforeEach
    protected void emptyCollections() {
        for (String collection : List.of("users", "accounts", "transactions", "audit_log", "scheduled_transfers")) {
            mongoTemplate.remove(new Query(), collection);
        }
    }

    // ----- Security helpers for API tests -----

    /** Requests with no login token. */
    protected MockMvc anonymousMockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /** Requests sent as the user this token belongs to ("Authorization: Bearer <token>"). */
    protected MockMvc mockMvcAs(String token) {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
    }

    /** Requests sent as bank staff. The test admin is created the first time it's needed. */
    protected MockMvc adminMockMvc() {
        return mockMvcAs(adminToken());
    }

    protected String adminToken() {
        User admin = usersForTests.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            long id = userServiceForTests.registerUser("Test Admin", ADMIN_EMAIL, ADMIN_ADDRESS, null, Role.ADMIN)
                    .userId();
            return usersForTests.findById(id).orElseThrow();
        });
        return tokenService.issue(admin).value();
    }

    /** A token for an existing user, as if they had just logged in. */
    protected String tokenFor(long userId) {
        return tokenService.issue(usersForTests.findById(userId).orElseThrow()).value();
    }
}
