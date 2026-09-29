package com.simplebank.repository;

import com.simplebank.model.User;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Searches users with a MongoDB aggregation pipeline: a list of stages that
 * documents flow through, each one filtering or reshaping them.
 *
 * Spring Data finds this class by its name (the fragment interface's name + "Impl")
 * and plugs it into UserRepository automatically.
 */
public class UserSearchRepositoryImpl implements UserSearchRepository {

    private final MongoTemplate mongoTemplate;

    public UserSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<User> search(UserFilter filter, Pageable pageable) {
        List<Document> pipeline = new ArrayList<>();

        // Stage: keep users whose address matches (city ignores upper/lowercase)
        Document addressMatch = new Document();
        if (filter.state() != null) {
            addressMatch.append("address.state", filter.state());
        }
        if (filter.city() != null) {
            addressMatch.append("address.city", new Document("$regex", "^" + Pattern.quote(filter.city()) + "$")
                    .append("$options", "i"));
        }
        if (filter.zip() != null) {
            addressMatch.append("address.zip", filter.zip());
        }
        if (!addressMatch.isEmpty()) {
            pipeline.add(new Document("$match", addressMatch));
        }

        if (filter.hasBalanceRange()) {
            // Stage: attach each user's accounts from the accounts collection (like a SQL JOIN)
            pipeline.add(new Document("$lookup", new Document("from", "accounts")
                    .append("localField", "_id")
                    .append("foreignField", "userId")
                    .append("as", "accounts")));

            Document range = BalanceRange.of(filter.minBalance(), filter.maxBalance());
            if (filter.balanceMode() == BalanceMode.ANY_ACCOUNT) {
                // Stage: keep users with at least one account in the range
                pipeline.add(new Document("$match", new Document("accounts",
                        new Document("$elemMatch", new Document("balance", range)))));
            } else {
                // Stages: add up all their balances (0 with no accounts), then keep totals in the range
                pipeline.add(new Document("$addFields",
                        new Document("totalBalance", new Document("$sum", "$accounts.balance"))));
                pipeline.add(new Document("$match", new Document("totalBalance", range)));
            }
        }

        // Stages: oldest first, then one page of results plus the total count, in one round trip
        pipeline.add(new Document("$sort", new Document("_id", 1)));
        pipeline.add(new Document("$facet", new Document()
                .append("content", List.of(
                        new Document("$skip", pageable.getOffset()),
                        new Document("$limit", pageable.getPageSize())))
                .append("total", List.of(new Document("$count", "count")))));

        Document result = mongoTemplate.getCollection("users").aggregate(pipeline).first();

        List<User> users = result.getList("content", Document.class).stream()
                .map(document -> mongoTemplate.getConverter().read(User.class, document))
                .toList();
        List<Document> total = result.getList("total", Document.class);
        long count = total.isEmpty() ? 0 : ((Number) total.get(0).get("count")).longValue();

        return new PageImpl<>(users, pageable, count);
    }
}
