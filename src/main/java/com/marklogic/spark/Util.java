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
package com.marklogic.spark;

import org.apache.spark.sql.catalyst.json.JSONOptions;
import scala.collection.immutable.HashMap;

import java.util.Map;
import java.util.stream.Stream;

public abstract class Util {

    public final static JSONOptions DEFAULT_JSON_OPTIONS = new JSONOptions(
        new HashMap<>(),

        // As verified via tests, this default timezone ID is overridden by a user via the spark.sql.session.timeZone option.
        "Z",

        // We don't expect corrupted records - i.e. corrupted values - to be present in the index. But Spark
        // requires this to be set. See
        // https://medium.com/@sasidharan-r/how-to-handle-corrupt-or-bad-record-in-apache-spark-custom-logic-pyspark-aws-430ddec9bb41
        // for more information.
        "_corrupt_record"
    );

    public final static boolean hasOption(Map<String, String> properties, String... options) {
        return Stream.of(options)
            .anyMatch(option -> properties.get(option) != null && properties.get(option).trim().length() > 0);
    }

}
