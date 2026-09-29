package com.simplebank.repository;

import com.mongodb.client.MongoCollection;
import com.simplebank.model.Account;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Searches accounts by balance range and type. Spring Data finds this class by its
 * name and plugs it into AccountRepository automatically.
 */
public class AccountSearchRepositoryImpl implements AccountSearchRepository {

    private final MongoTemplate mongoTemplate;

    public AccountSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Account> search(AccountFilter filter, Pageable pageable) {
        Document query = new Document();
        if (filter.accountType() != null) {
            query.append("accountType", filter.accountType().name());
        }
        Document range = BalanceRange.of(filter.minBalance(), filter.maxBalance());
        if (range != null) {
            query.append("balance", range);
        }

        MongoCollection<Document> accounts = mongoTemplate.getCollection("accounts");
        long count = accounts.countDocuments(query);

        List<Account> content = new ArrayList<>();
        accounts.find(query)
                .sort(new Document("_id", 1))
                .skip((int) pageable.getOffset())
                .limit(pageable.getPageSize())
                .forEach(document -> content.add(mongoTemplate.getConverter().read(Account.class, document)));

        return new PageImpl<>(content, pageable, count);
    }
}
