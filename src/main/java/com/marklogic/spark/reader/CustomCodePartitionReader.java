package com.marklogic.spark.reader;

import com.marklogic.client.eval.EvalResultIterator;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.spark.CustomCodeContext;
import com.marklogic.spark.Options;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.unsafe.types.UTF8String;

class CustomCodePartitionReader implements PartitionReader {

    private final ServerEvaluationCall serverEvaluationCall;
    private final boolean isCustomSchema;

    private EvalResultIterator evalResultIterator;
    private final JsonRowDeserializer jsonRowDeserializer;

    public CustomCodePartitionReader(CustomCodeContext customCodeContext) {
        this.serverEvaluationCall = customCodeContext.buildCall(
            customCodeContext.connectToMarkLogic(),
            new CustomCodeContext.CallInputs(
                Options.READ_INVOKE, Options.READ_JAVASCRIPT, Options.READ_XQUERY, Options.READ_VARS_PREFIX
            )
        );
        this.isCustomSchema = customCodeContext.isCustomSchema();
        this.jsonRowDeserializer = new JsonRowDeserializer(customCodeContext.getSchema());
    }

    @Override
    public boolean next() {
        if (this.evalResultIterator == null) {
            this.evalResultIterator = this.serverEvaluationCall.eval();
        }
        return this.evalResultIterator.hasNext();
    }

    @Override
    public InternalRow get() {
        String val = this.evalResultIterator.next().getString();
        if (this.isCustomSchema) {
            return this.jsonRowDeserializer.deserializeJson(val);
        }
        return new GenericInternalRow(new Object[]{UTF8String.fromString(val)});
    }

    @Override
    public void close() {
        if (this.evalResultIterator != null) {
            this.evalResultIterator.close();
        }
    }
}
