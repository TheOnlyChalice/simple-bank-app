package com.simplebank;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.List;

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
 */
@SpringBootTest
public abstract class MongoTestBase {

    private static final int MONGO_PORT = 27017;

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

    @BeforeEach
    protected void emptyCollections() {
        for (String collection : List.of("users", "accounts", "transactions", "audit_log")) {
            mongoTemplate.remove(new Query(), collection);
        }
    }
}
