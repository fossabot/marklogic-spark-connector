package com.marklogic.spark.reader;

import com.marklogic.spark.AbstractIntegrationTest;
import com.marklogic.spark.Options;
import org.apache.spark.sql.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadWithClientUriTest extends AbstractIntegrationTest {

    @Test
    void validUri() {
        List<Row> rows = readRowsWithClientUri(String.format(
            "%s:%s@%s:%d",
            TEST_USERNAME, TEST_PASSWORD, testConfig.getHost(), testConfig.getRestPort()
        ));
        assertEquals(15, rows.size());
    }

    @Test
    void uriWithDatabase() {
        List<Row> rows = readRowsWithClientUri(String.format(
            "%s:%s@%s:%d/spark-test-test-content",
            TEST_USERNAME, TEST_PASSWORD, testConfig.getHost(), testConfig.getRestPort()
        ));
        assertEquals(15, rows.size());
    }

    @Test
    void uriWithInvalidDatabase() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> readRowsWithClientUri(String.format(
            "%s:%s@%s:%d/database-doesnt-exist",
            TEST_USERNAME, TEST_PASSWORD, testConfig.getHost(), testConfig.getRestPort()
        )));

        assertTrue(ex.getMessage().contains("XDMP-NOSUCHDB: No such database database-doesnt-exist"),
            "Unexpected error: " + ex.getMessage());
    }

    @Test
    void missingAtSymbol() {
        verifyClientUriIsInvalid("has no 'at' symbol");
    }

    @Test
    void twoAtSymbols() {
        verifyClientUriIsInvalid("user@host@port");
    }

    @Test
    void onlyOneTokenBeforeAtSymbol() {
        verifyClientUriIsInvalid("user@host:port");
    }

    @Test
    void onlyOneTokenAfterAtSymbol() {
        verifyClientUriIsInvalid("user:password@host");
    }

    @Test
    void threeTokensBeforeAtSymbol() {
        verifyClientUriIsInvalid("user:password:something@host:port");
    }

    @Test
    void threeTokensAfterAtSymbol() {
        verifyClientUriIsInvalid("user:password@host:port:something");
    }

    private List<Row> readRowsWithClientUri(String clientUri) {
        return newSparkSession()
            .read()
            .format("com.marklogic.spark")
            .option(Options.CLIENT_URI, clientUri)
            .option(ReadConstants.OPTIC_DSL, "op.fromView('Medical','Authors')")
            .load()
            .collectAsList();
    }

    private void verifyClientUriIsInvalid(String clientUri) {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> readRowsWithClientUri(clientUri)
        );

        assertEquals(
            "Invalid value for spark.marklogic.client.uri; must be username:password@host:port",
            ex.getMessage()
        );
    }
}
