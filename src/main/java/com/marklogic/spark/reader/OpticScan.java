/*
 * Copyright 2023 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marklogic.spark.reader;

import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.streaming.MicroBatchStream;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpticScan implements Scan {

    private final static Logger logger = LoggerFactory.getLogger(OpticScan.class);

    private ReadContext readContext;

    OpticScan(ReadContext readContext) {
        this.readContext = readContext;
    }

    @Override
    public StructType readSchema() {
        return readContext.getSchema();
    }

    @Override
    public String description() {
        return Scan.super.description();
    }

    @Override
    public Batch toBatch() {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating new batch");
        }
        return new OpticBatch(readContext);
    }

    @Override
    public MicroBatchStream toMicroBatchStream(String checkpointLocation) {
        return new OpticMicroBatchStream(readContext);
    }
}
