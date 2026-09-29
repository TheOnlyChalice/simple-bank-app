package com.simplebank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Pings MongoDB when the app starts. The driver otherwise connects lazily, so a wrong
 * password or a blocked IP address would only show up on the first request. This makes
 * the app fail at startup instead, with a clear error ("fail fast").
 */
@Component
public class MongoConnectionCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoConnectionCheck.class);

    private final MongoTemplate mongoTemplate;

    public MongoConnectionCheck(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.executeCommand("{ ping: 1 }");
        log.info("Connected to MongoDB database '{}'", mongoTemplate.getDb().getName());
    }
}
