package com.example.slotmind.engine;

import java.util.List;

/**
 * The scheduling contract. Implement it and annotate the class with @Component to register it
 * (a future RoomConstraintStrategy needs nothing more). Return options best-first; an empty list
 * means nothing suitable.
 */
public interface SchedulingStrategy {

    /** Unique key used in config and the API, e.g. "rule". */
    String name();

    List<Recommendation> recommend(SchedulingContext context);
}
