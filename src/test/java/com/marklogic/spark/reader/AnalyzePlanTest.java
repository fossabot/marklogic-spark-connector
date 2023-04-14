package com.marklogic.spark.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.marklogic.client.impl.DatabaseClientImpl;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.row.RawQueryDSLPlan;
import com.marklogic.client.row.RowManager;
import com.marklogic.spark.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnalyzePlanTest extends AbstractIntegrationTest {

    private RowManager rowManager;

    @BeforeEach
    void setup() {
        rowManager = getDatabaseClient().newRowManager();
    }

    @ParameterizedTest
    @CsvSource({
        "1,1",
        "1,2",
        "1,16",
        "2,1",
        "2,2",
        "2,16",
        // Using more partitions than there are results isn't an efficient choice by the user,
        // but it should still work fine - some buckets just won't return anything.
        "16,1",
        "16,2",
        "16,16"
    })
    void partitionCountAndBatchSize(int partitionCount, int batchSize) {
        logger.info(partitionCount + ":" + batchSize);

        PlanAnalysis planAnalysis = analyzePlan(partitionCount, batchSize);
        verifyBucketsCoverAllUnsignedLongs(planAnalysis);
        verifyAllFifteenAuthorsAreReturned(planAnalysis);
    }

    private PlanAnalysis analyzePlan(int partitionCount, int batchSize) {
        RawQueryDSLPlan userPlan = rowManager.newRawQueryDSLPlan(new StringHandle("op.fromView('Medical', 'Authors')"));
        PlanAnalyzer partitioner = new PlanAnalyzer((DatabaseClientImpl) getDatabaseClient());
        PlanAnalysis planAnalysis = partitioner.analyzePlan(userPlan.getHandle(), partitionCount, batchSize);
        assertEquals(partitionCount, planAnalysis.partitions.size());
        return planAnalysis;
    }

    /**
     * Verifies that the sequence of buckets across all the partitions covers all unsigned longs from 0 to max unsigned
     * long.
     *
     * @param planAnalysis
     */
    private void verifyBucketsCoverAllUnsignedLongs(PlanAnalysis planAnalysis) {
        List<PlanAnalysis.Bucket> allBuckets = planAnalysis.getAllBuckets();

        assertEquals("0", allBuckets.get(0).lowerBound, "The first bucket in the first partition should have a lower " +
            "bound of the lowest unsigned long, which is zero.");
        assertEquals("18446744073709551615", allBuckets.get(allBuckets.size() - 1).upperBound, "The last bucket in the " +
            "last partition should have the highest unsigned long as its upper bound.");

        for (int i = 1; i < allBuckets.size(); i++) {
            PlanAnalysis.Bucket bucket = allBuckets.get(i);
            PlanAnalysis.Bucket previousBucket = allBuckets.get(i - 1);
            assertEquals(Long.parseUnsignedLong(bucket.lowerBound), Long.parseUnsignedLong(previousBucket.upperBound) + 1,
                "The lower bound of each bucket should be 1 more than the upper bound of the previous bucket.");
        }
    }

    /**
     * Runs the plan for each bucket, ensuring that all 15 authors are returned.
     *
     * @param planAnalysis
     */
    private void verifyAllFifteenAuthorsAreReturned(PlanAnalysis planAnalysis) {
        // Run the first bucket plan to get the serverTimestamp
        JacksonHandle initialHandle = new JacksonHandle();
        runPlan(planAnalysis, planAnalysis.partitions.get(0).buckets.get(0), initialHandle);
        final long serverTimestamp = initialHandle.getServerTimestamp();

        // Now run the plan on each bucket and keep track of the total number of rows returned.
        // This uses a thread pool solely to improve the performance of the test.
        ExecutorService executor = Executors.newFixedThreadPool(planAnalysis.getAllBuckets().size());
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger returnedRowCount = new AtomicInteger();
        for (PlanAnalysis.Partition partition : planAnalysis.partitions) {
            for (PlanAnalysis.Bucket bucket : partition.buckets) {
                futures.add(executor.submit(() -> {
                    JacksonHandle resultHandle = new JacksonHandle();
                    resultHandle.setPointInTimeQueryTimestamp(serverTimestamp);
                    JsonNode result = runPlan(planAnalysis, bucket, resultHandle);
                    // It's fine if a bucket has no rows, in which case the result will be null
                    if (result != null) {
                        returnedRowCount.addAndGet(result.get("rows").size());
                    }
                }));
            }
        }

        // Wait for all the threads to finish
        futures.forEach(future -> {
            try {
                future.get();
            } catch (Exception ex) {
                // Ignore
            }
        });

        assertEquals(15, returnedRowCount.get(),
            "All 15 author rows should have been returned; we can't assume how many will be in a bucket since the " +
                "row ID of each row is random, we just know we should get 15 back.");
    }

    private JsonNode runPlan(PlanAnalysis plan, PlanAnalysis.Bucket bucket, JacksonHandle resultHandle) {
        return rowManager.resultDoc(
            rowManager.newRawPlanDefinition(new JacksonHandle(plan.boundedPlan))
                .bindParam("ML_LOWER_BOUND", bucket.lowerBound)
                .bindParam("ML_UPPER_BOUND", bucket.upperBound),
            resultHandle
        ).get();
    }
}
