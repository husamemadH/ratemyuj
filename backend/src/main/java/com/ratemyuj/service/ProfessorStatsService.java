package com.ratemyuj.service;

import com.ratemyuj.domain.Professor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Maintains the denormalized rating aggregates on the professor document.
 *
 * Concurrency design:
 *  1. Counters (reviewCount, ratingSum, star buckets) change via atomic $inc
 *     through findAndModify(returnNew) — always correct under contention.
 *  2. avgRating is derived from the returned snapshot and written with an
 *     optimistic guard (matches only if counters are unchanged). If another
 *     writer got in between, our guarded write matches 0 documents and the
 *     later writer — which saw the newer counters — sets the correct average.
 */
@Service
public class ProfessorStatsService {

    private final MongoTemplate mongo;

    public ProfessorStatsService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void applyNewRating(String professorId, int rating) {
        applyDelta(professorId, rating, +1);
    }

    public void removeRating(String professorId, int rating) {
        applyDelta(professorId, rating, -1);
    }

    public void replaceRating(String professorId, int oldRating, int newRating) {
        if (oldRating == newRating) return;
        Update update = new Update()
                .inc("ratingSum", newRating - oldRating)
                .inc(bucketField(oldRating), -1)
                .inc(bucketField(newRating), +1);
        Professor snapshot = mongo.findAndModify(
                byId(professorId), update,
                FindAndModifyOptions.options().returnNew(true),
                Professor.class);
        if (snapshot != null) writeAverage(snapshot);
    }

    private void applyDelta(String professorId, int rating, int direction) {
        Update update = new Update()
                .inc("reviewCount", direction)
                .inc("ratingSum", rating * direction)
                .inc(bucketField(rating), direction);
        Professor snapshot = mongo.findAndModify(
                byId(professorId), update,
                FindAndModifyOptions.options().returnNew(true),
                Professor.class);
        if (snapshot != null) writeAverage(snapshot);
    }

    private void writeAverage(Professor snapshot) {
        double avg = snapshot.getReviewCount() <= 0
                ? 0.0
                : Math.round((double) snapshot.getRatingSum() / snapshot.getReviewCount() * 10.0) / 10.0;

        // guarded write: only lands if the counters we derived from are still current
        Query guard = Query.query(Criteria.where("_id").is(snapshot.getId())
                .and("reviewCount").is(snapshot.getReviewCount())
                .and("ratingSum").is(snapshot.getRatingSum()));
        mongo.updateFirst(guard, Update.update("avgRating", avg), Professor.class);
    }

    private Query byId(String id) {
        return Query.query(Criteria.where("_id").is(id));
    }

    private String bucketField(int rating) {
        return "breakdown." + switch (rating) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            default -> "five";
        };
    }
}
