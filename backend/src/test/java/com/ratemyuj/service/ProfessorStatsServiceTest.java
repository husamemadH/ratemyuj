package com.ratemyuj.service;

import com.ratemyuj.domain.Professor;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfessorStatsServiceTest {

    @Mock private MongoTemplate mongo;
    @InjectMocks private ProfessorStatsService service;

    private Professor snapshot(String id, int count, int sum) {
        Professor p = new Professor();
        p.setId(id);
        p.setReviewCount(count);
        p.setRatingSum(sum);
        return p;
    }

    @Test
    @DisplayName("a new 5-star rating increments count, sum, and the right star bucket")
    void applyNewRatingIncrementsCounters() {
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Professor.class)))
                .thenReturn(snapshot("p1", 2, 9));

        service.applyNewRating("p1", 5);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(Professor.class));

        Document inc = update.getValue().getUpdateObject().get("$inc", Document.class);
        assertThat(inc.getInteger("reviewCount")).isEqualTo(1);
        assertThat(inc.getInteger("ratingSum")).isEqualTo(5);
        assertThat(inc.getInteger("breakdown.five")).isEqualTo(1);
    }

    @Test
    @DisplayName("average is derived from the snapshot and written behind a counter guard")
    void averageWriteIsGuarded() {
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Professor.class)))
                .thenReturn(snapshot("p1", 2, 9));

        service.applyNewRating("p1", 5);

        ArgumentCaptor<Query> guard = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> avgWrite = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(guard.capture(), avgWrite.capture(), eq(Professor.class));

        Document query = guard.getValue().getQueryObject();
        assertThat(query.get("_id")).isEqualTo("p1");
        assertThat(query.getInteger("reviewCount")).isEqualTo(2);   // optimistic guard
        assertThat(query.getInteger("ratingSum")).isEqualTo(9);

        Document set = avgWrite.getValue().getUpdateObject().get("$set", Document.class);
        assertThat(set.getDouble("avgRating")).isEqualTo(4.5);       // 9 / 2
    }

    @Test
    @DisplayName("removing the last rating resets the average to 0.0, never NaN")
    void lastRatingRemovalYieldsZeroAverage() {
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Professor.class)))
                .thenReturn(snapshot("p1", 0, 0));

        service.removeRating("p1", 4);

        ArgumentCaptor<Update> avgWrite = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), avgWrite.capture(), eq(Professor.class));
        Document set = avgWrite.getValue().getUpdateObject().get("$set", Document.class);
        assertThat(set.getDouble("avgRating")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("replaceRating with the same value is a no-op")
    void replaceWithSameRatingDoesNothing() {
        service.replaceRating("p1", 3, 3);
        verifyNoInteractions(mongo);
    }

    @Test
    @DisplayName("replaceRating adjusts sum and swaps star buckets without touching count")
    void replaceSwapsBuckets() {
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Professor.class)))
                .thenReturn(snapshot("p1", 3, 11));

        service.replaceRating("p1", 2, 5);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(Professor.class));
        Document inc = update.getValue().getUpdateObject().get("$inc", Document.class);
        assertThat(inc.containsKey("reviewCount")).isFalse();
        assertThat(inc.getInteger("ratingSum")).isEqualTo(3);        // 5 - 2
        assertThat(inc.getInteger("breakdown.two")).isEqualTo(-1);
        assertThat(inc.getInteger("breakdown.five")).isEqualTo(1);
    }

    @Test
    @DisplayName("unknown professor id: no average write is attempted")
    void missingProfessorSkipsAverageWrite() {
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Professor.class)))
                .thenReturn(null);

        service.applyNewRating("ghost", 5);

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(Professor.class));
    }
}
