/**
 * Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.bam.utils.persistence;

import java.util.List;

public class BatchedResult {

    private List<ResultRow> results;

    private String cursor;

    public BatchedResult(List<ResultRow> results, String cursor) {
        this.results = results;
        this.cursor = cursor;
    }

    public List<ResultRow> getResults() {
        return results;
    }

    public void setResults(List<ResultRow> results) {
        this.results = results;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

}
