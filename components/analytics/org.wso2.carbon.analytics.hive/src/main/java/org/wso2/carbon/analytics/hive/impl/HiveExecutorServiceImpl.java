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
package org.wso2.carbon.analytics.hive.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.service.Utils;
import org.wso2.carbon.analytics.hive.ServiceHolder;
import org.wso2.carbon.analytics.hive.conf.HiveConnectionManager;
import org.wso2.carbon.analytics.hive.dto.QueryResult;
import org.wso2.carbon.analytics.hive.dto.QueryResultRow;
import org.wso2.carbon.analytics.hive.exception.HiveConnectionException;
import org.wso2.carbon.analytics.hive.exception.HiveExecutionException;
import org.wso2.carbon.analytics.hive.service.HiveExecutorService;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HiveExecutorServiceImpl implements HiveExecutorService {

    private static final Log log = LogFactory.getLog(HiveExecutorServiceImpl.class);

    private boolean initialized;

    // TODO: Use datasource component instead of explicitly creating connections
    public void initialize(String driverName) {
        try {
            Class.forName(driverName);
        } catch (ClassNotFoundException e) {
            log.error("Error during initialization of Hive driver", e);
        }

        this.initialized = true;

    }

    /**
     * @param script
     * @return The Resultset of all executed queries in the script
     * @throws HiveExecutionException
     */
    public QueryResult[] execute(String script) throws HiveExecutionException {
        if (script != null) {
            /*HiveConnectionManager confManager = HiveConnectionManager.getInstance();

            if (!initialized) {
                initialize(confManager.getConfValue(HiveConstants.HIVE_DRIVER_KEY));
            }*/

            Connection con;
            try {
                con = ServiceHolder.getConnectionManager().getHiveConnection();
            } catch (HiveConnectionException e) {
                throw new HiveExecutionException("Error while connecting to Hive service..", e);
            }

            try {
/*                con = DriverManager.getConnection(confManager.
                        getConfValue(HiveConstants.HIVE_URL_KEY), confManager.
                        getConfValue(HiveConstants.HIVE_USERNAME_KEY), confManager.
                        getConfValue(HiveConstants.HIVE_PASSWORD_KEY));*/
                Statement stmt = con.createStatement(); // TODO: Use datasource

                Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
                Matcher regexMatcher = regex.matcher(script);
                String formattedScript = "";
                while (regexMatcher.find()) {
                    String temp = "";
                    if (regexMatcher.group(1) != null) {
                        // Add double-quoted string without the quotes
                        temp = regexMatcher.group(1).replaceAll(";", "%%");
                        temp = "\"" + temp + "\"";
                    } else if (regexMatcher.group(2) != null) {
                        // Add single-quoted string without the quotes
                        temp = regexMatcher.group(2).replaceAll(";", "%%");
                        temp = "\'" + temp + "\'";
                    } else {
                        temp = regexMatcher.group();
                    }
                    formattedScript += temp + " ";
                }


                String[] cmdLines = formattedScript.split(";\\r?\\n|;"); // Tokenize with ;[new-line]

                List<QueryResult> queryResults = new ArrayList<QueryResult>();

                /* When we call executeQuery, execution start in separate thread (started by thrift thread pool),
                   therefore we can't get tenant ID from that thread. So we are appending the tenant ID to each query
                   in order to get it from hive side.
                 */
                int tenantId = CarbonContextHolder.getCurrentCarbonContextHolder().getTenantId();

                for (String cmdLine : cmdLines) {

                    String trimmedCmdLine = cmdLine.trim();
                    trimmedCmdLine = trimmedCmdLine.replaceAll(";", "");
                    trimmedCmdLine = trimmedCmdLine.replaceAll("%%", ";");
                    if (!"".equals(trimmedCmdLine)) {
                        QueryResult queryResult = new QueryResult();

                        //Append the tenant ID to query
                        trimmedCmdLine += Utils.TENANT_ID_SEPARATOR_CHAR_SEQ + tenantId;

                        queryResult.setQuery(trimmedCmdLine);

                        ResultSet rs = stmt.executeQuery(trimmedCmdLine);
                        ResultSetMetaData metaData = rs.getMetaData();

                        int columnCount = metaData.getColumnCount();
                        List<String> columnsList = new ArrayList<String>();
                        for (int i = 1; i <= columnCount; i++) {
                            columnsList.add(metaData.getColumnName(i));
                        }

                        queryResult.setColumnNames(columnsList.toArray(new String[]{}));

                        List<QueryResultRow> results = new ArrayList<QueryResultRow>();
                        while (rs.next()) {
                            QueryResultRow resultRow = new QueryResultRow();

                            List<String> columnValues = new ArrayList<String>();
                            for (int i = 1; i <= columnCount; i++) {
                                Object resObj = rs.getObject(i);
                                if (null != resObj) {
                                    columnValues.add(rs.getObject(i).toString());
                                } else {
                                    columnValues.add("");
                                }
                            }

                            resultRow.setColumnValues(columnValues.toArray(new String[]{}));

                            results.add(resultRow);
                        }

                        queryResult.setResultRows(results.toArray(new QueryResultRow[]{}));
                        queryResults.add(queryResult);
                    }

                }

                return queryResults.toArray(new QueryResult[]{});


            } catch (SQLException e) {
                throw new HiveExecutionException("Error while executing Hive script.\n" + e.getMessage(), e);
            } finally {
                if (null != con) {
                    try {
                        con.close();
                    } catch (SQLException e) {
                    }
                }
            }

        }

        return null;

    }

    @Override
    public boolean setConnectionParameters(String driverName, String url, String username,
                                           String password) {
        Connection con = null;
        try {
            Class.forName(driverName);
            con = DriverManager.getConnection(url, username, password);
            HiveConnectionManager connectionManager = HiveConnectionManager.getInstance();
            connectionManager.saveConfiguration(driverName, url, username, password);
        } catch (ClassNotFoundException e) {
            log.error("Error during initialization of Hive driver", e);
        } catch (SQLException e) {
            log.error("URL | Username | password in incorrect. Unable to connect to hive");
        } finally {
            if (null != con) {
                try {
                    con.close();
                } catch (SQLException e) {
                }
                return true;
            } else {
                return false;
            }
        }

    }
}
