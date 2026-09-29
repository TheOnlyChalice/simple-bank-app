package com.simplebank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A document in the "users" collection.
 * The numeric ID is assigned from the "counters" collection when the user is first saved.
 */
@Document(collection = "users")
@CompoundIndex(name = "address_location", def = "{'address.state': 1, 'address.city': 1}")
public class User {

    @Id
    private Long userId;

    private String name;

    /** Unique index: MongoDB itself rejects a second user with the same email. */
    @Indexed(unique = true)
    private String email;

    /** Embedded document. The index above makes searching by state and city fast. */
    private Address address;

    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String email, Address address) {
        this.name = name;
        this.email = email;
        this.address = address;
        this.createdAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
