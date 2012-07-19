/*
 *  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.rssmanager.core.internal.util;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.Base64;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.core.internal.DataSourceServiceComponent;
import org.wso2.carbon.ndatasource.rdbms.RDBMSConfiguration;
import org.wso2.carbon.ndatasource.rdbms.RDBMSDataSource;
import org.wso2.carbon.rssmanager.common.RSSManagerCommonUtil;
import org.wso2.carbon.rssmanager.common.RSSManagerConstants;
import org.wso2.carbon.rssmanager.core.RSSManagerException;
import org.wso2.carbon.rssmanager.core.internal.RSSManagerServiceComponent;
import org.wso2.carbon.rssmanager.core.internal.entity.*;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.util.*;

public class RSSManagerUtil {

    //private static SecretResolver secretResolver;

    /**
     * Retrieves the list of tenantIDs of the currently loaded tenants
     *
     * @return Tenant ID list
     * @throws RSSManagerException Thrown when an error occurs while retrieving the list of
     *                             tenants via the Tenant Tanager.
     */
    public static List<Integer> getAllTenants() throws RSSManagerException {
        List<Integer> tenantIds = new ArrayList<Integer>();
        TenantManager tenantMgr = RSSManagerServiceComponent.getTenantManager();
        if (tenantMgr != null) {
            try {
                for (Tenant tenant : tenantMgr.getAllTenants()) {
                    tenantIds.add(tenant.getId());
                }
                tenantIds.add(MultitenantConstants.SUPER_TENANT_ID);
            } catch (UserStoreException e) {
                throw new RSSManagerException("Error while retrieving tenant data", e);
            }
        }
        return tenantIds;
    }

    /**
     * Retrieves the tenant domain name for a given tenant ID
     *
     * @param tenantId Tenant Id
     * @return Domain name of corresponds to the provided tenant ID
     * @throws RSSManagerException Thrown when there's any error while retrieving the tenant
     *                             domain for the provided tenant ID
     */
    public static String getTenantDomainFromTenantId(int tenantId) throws RSSManagerException {
        TenantManager tenantMgr = RSSManagerServiceComponent.getTenantManager();
        try {
            return tenantMgr.getDomain(tenantId);
        } catch (UserStoreException e) {
            throw new RSSManagerException("Error occurred while retrieving tenant domain for " +
                    "the given tenant ID");
        }
    }
    
    /**
     * Returns the fully qualified name of the database to be created. This will append an
     * underscore and the tenant's domain name to the database to make it unique for that particular
     * tenant. It will return the database name as it is, if it is created in Super tenant mode.
     *
     * @param databaseName Name of the database
     * @return Fully qualified name of the database
     */
    public static String getFullyQualifiedDatabaseName(String databaseName) {
        String tenantDomain =
                CarbonContextHolder.getCurrentCarbonContextHolder().getTenantDomain();
        if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            return databaseName + "_" + RSSManagerCommonUtil.processDomainName(tenantDomain);
        }
        return databaseName;
    }

    public static Map<String, Object> convertToDatabasePrivilegeMap(DatabasePrivilege[] privileges) {
        Map<String, Object> privMap = new HashMap<String, Object>();
        for (DatabasePrivilege privilege : privileges) {
            privMap.put(privilege.getName(), privilege.getValue());
        }
        return privMap;
    }

    /**
     * Returns the fully qualified username of a particular database user. For an ordinary tenant,
     * the tenant domain will be appended to the username together with an underscore and the given
     * username will be returned as it is in the case of super tenant.
     *
     * @param username Username of the database user.
     * @return Fully qualified username of the database user.
     */
    public static String getFullyQualifiedUsername(String username) {
        String tenantDomain = CarbonContextHolder.getCurrentCarbonContextHolder().getTenantDomain();
        if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {

            /* The maximum number of characters allowed for the username in mysql system tables is
             * 16. Thus, to adhere the aforementioned constraint as well as to give the username
             * an unique identification based on the tenant domain, we append a hash value that is
             * created based on the tenant domain */
            byte[] bytes = RSSManagerCommonUtil.intToByteArray(tenantDomain.hashCode());
            return username + "_" + Base64.encode(bytes);
        }
        return username;
    }

    /**
     * Util method to prepare JDBC url of a particular RSS instance to be a valid url to be stored
     * in the metadata repository.
     *
     * @param url    JDBC url.
     * @param dbName Name of the database instance.
     * @return Processed JDBC url.
     */
    public static String processJdbcUrl(String url, String dbName) {
        if (url != null && !"".equals(url)) {
            return url.endsWith("/") ? (url + dbName) : (url + "/" + dbName);
        }
        return "";
    }

    /**
     * Util method to prepareUpdateSqlQueries corresponding to data manipulations done against
     * the system databases.
     *
     * @param queryPrefix queryPrefix.
     * @param querySuffix querySuffix.
     * @param privileges  list of privileges.
     * @return sqlQuery for updating system database entities.
     */
    public static String prepareUpdateSQLString(String queryPrefix, String querySuffix,
                                                List<String> privileges) {
        StringBuilder sql = new StringBuilder(queryPrefix);
        for (int i = 0; i < privileges.size(); i++) {
            if (i != privileges.size() - 1) {
                sql.append(privileges.get(i)).append("=?,");
            } else {
                sql.append(privileges.get(i)).append("=?");
            }
        }
        sql.append(querySuffix);
        return sql.toString();
    }

    public static RSSInstanceMetaData convertToRSSInstanceMetaData(RSSInstance rssIns) throws
            RSSManagerException {
        RSSInstanceMetaData metadata = new RSSInstanceMetaData();
        metadata.setName(rssIns.getName());
        metadata.setServerUrl(rssIns.getServerURL());
        metadata.setInstanceType(rssIns.getInstanceType());
        metadata.setServerCategory(rssIns.getServerCategory());
        String tenantDomain = RSSManagerUtil.getTenantDomainFromTenantId(rssIns.getTenantId());
        metadata.setTenantDomainName(tenantDomain);

        return metadata;
    }

    public static DatabaseMetaData convertToDatabaseMetaData(Database database) throws
            RSSManagerException {
        DatabaseMetaData metadata = new DatabaseMetaData();
        String fullyQualifiedDatabaseName =
                RSSManagerUtil.getFullyQualifiedDatabaseName(database.getName());
        metadata.setName(fullyQualifiedDatabaseName);
        metadata.setRssInstanceName(metadata.getRssInstanceName());
        //metadata.setDatabaseURL(database.);
        String tenantDomain = RSSManagerUtil.getTenantDomainFromTenantId(database.getTenantId());
        metadata.setRssTenantDomain(tenantDomain);

        return metadata;
    }

    public static DataSource createDataSource(OMElement dsEl) throws RSSManagerException {
        Properties dsProps = RSSManagerUtil.populateDataSourceProperties(dsEl);

        RDBMSConfiguration dsConfig = new RDBMSConfiguration();
        dsConfig.setDriverClassName(dsProps.getProperty(RSSManagerConstants.DRIVER_NAME));
        if (dsConfig.getDriverClassName() == null) {
            return null;
        }
        dsConfig.setUrl(dsProps.getProperty(RSSManagerConstants.URL));
        dsConfig.setUsername(dsProps.getProperty(RSSManagerConstants.USER_NAME));
        dsConfig.setPassword(dsProps.getProperty(RSSManagerConstants.PASSWORD));

        if (dsProps.getProperty(RSSManagerConstants.MAX_ACTIVE) != null
                && !dsProps.getProperty(RSSManagerConstants.MAX_ACTIVE).equals("")) {
            dsConfig.setMaxActive(Integer.parseInt(dsProps.getProperty(
                    RSSManagerConstants.MAX_ACTIVE)));
        } else {
            dsConfig.setMaxActive(RSSManagerConstants.DEFAULT_MAX_ACTIVE);
        }

        if (dsProps.getProperty(RSSManagerConstants.MIN_IDLE) != null
                && !dsProps.getProperty(RSSManagerConstants.MIN_IDLE).equals("")) {
            dsConfig.setMinIdle(Integer.parseInt(dsProps.getProperty(
                    RSSManagerConstants.MIN_IDLE)));
        } else {
            dsConfig.setMinIdle(RSSManagerConstants.DEFAULT_MIN_IDLE);
        }

        if (dsProps.getProperty(RSSManagerConstants.MAX_IDLE) != null
                && !dsProps.getProperty(RSSManagerConstants.MAX_IDLE).equals("")) {
            dsConfig.setMinIdle(Integer.parseInt(dsProps.getProperty(
                    RSSManagerConstants.MAX_IDLE)));
        } else {
            dsConfig.setMinIdle(RSSManagerConstants.DEFAULT_MAX_IDLE);
        }

        if (dsProps.getProperty(RSSManagerConstants.MAX_WAIT) != null
                && !dsProps.getProperty(RSSManagerConstants.MAX_WAIT).equals("")) {
            dsConfig.setMaxWait(Integer.parseInt(dsProps.getProperty(
                    RSSManagerConstants.MAX_WAIT)));
        } else {
            dsConfig.setMaxWait(RSSManagerConstants.DEFAULT_MAX_WAIT);
        }

        if (dsProps.getProperty(RSSManagerConstants.TEST_WHILE_IDLE) != null
                && !dsProps.getProperty(
                RSSManagerConstants.TEST_WHILE_IDLE).equals("")) {
            dsConfig.setTestWhileIdle(Boolean.parseBoolean(dsProps.getProperty(
                    RSSManagerConstants.TEST_WHILE_IDLE)));
        }

        if (dsProps.getProperty(RSSManagerConstants.TIME_BETWEEN_EVICTION_RUNS_MILLIS) != null
                && !dsProps.getProperty(
                RSSManagerConstants.TIME_BETWEEN_EVICTION_RUNS_MILLIS).equals("")) {
            dsConfig.setTimeBetweenEvictionRunsMillis(Integer.parseInt(
                    dsProps.getProperty(
                            RSSManagerConstants.TIME_BETWEEN_EVICTION_RUNS_MILLIS)));
        }

        if (dsProps.getProperty(RSSManagerConstants.MIN_EVIC_TABLE_IDLE_TIME_MILLIS) != null
                && !dsProps.getProperty(
                RSSManagerConstants.MIN_EVIC_TABLE_IDLE_TIME_MILLIS).equals("")) {
            dsConfig.setMinEvictableIdleTimeMillis(Integer.parseInt(dsProps.getProperty(
                    RSSManagerConstants.MIN_EVIC_TABLE_IDLE_TIME_MILLIS)));
        }


        if (dsProps.getProperty(RSSManagerConstants.VALIDATION_QUERY) != null) {
            dsConfig.setValidationQuery(dsProps.getProperty(
                    RSSManagerConstants.VALIDATION_QUERY));
        }
        try {
            return (new RDBMSDataSource(dsConfig)).getDataSource();
        } catch (DataSourceException e) {
            throw new RuntimeException("Error in creating data source: " + e.getMessage(), e);
        }
    }

    public static DataSource createDataSource(RDBMSConfiguration config) {
        try {
            RDBMSDataSource dataSource = new RDBMSDataSource(config);
            return dataSource.getDataSource();
        } catch (DataSourceException e) {
            throw new RuntimeException("Error in creating data sourc: " + e.getMessage(), e);
        }
    }

    public static DataSource lookupDataSource(String dataSourceName) {
        try {
            return (DataSource) InitialContext.doLookup(dataSourceName);
        } catch (Exception e) {
            throw new RuntimeException("Error in looking up data source: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Properties populateDataSourceProperties(OMElement dsEl) throws RSSManagerException {
        Properties props = new Properties();
        Iterator<OMElement> propItr = dsEl.getChildElements();
        if (!propItr.hasNext()) {
            throw new RSSManagerException("RSS management repository database configuration " +
                        "is missing");
        }
        while (propItr.hasNext()) {
            OMElement propEl = propItr.next();
            String propValue = propEl.getText();
            if (propValue != null) {
                propValue = propValue.trim();
            }
            props.put(propEl.getLocalName(), propValue);
        }
        return props;
    }

    public static RSSInstanceMetaData convertRSSInstanceToMetadata(RSSInstance rssIns) throws
            RSSManagerException {
        RSSInstanceMetaData metadata = new RSSInstanceMetaData();
        metadata.setName(rssIns.getName());
        metadata.setServerUrl(rssIns.getServerURL());
        metadata.setInstanceType(rssIns.getDbmsType());
        metadata.setServerCategory(rssIns.getServerCategory());
        metadata.setTenantDomainName(getTenantDomainFromTenantId(rssIns.getTenantId()));
        return metadata;
    }

    public static DatabaseMetaData convertDatabaseToMetadata(Database database) throws
            RSSManagerException {
        DatabaseMetaData metadata = new DatabaseMetaData();
        metadata.setName(database.getName());
        metadata.setRssInstanceName(database.getRssInstanceName());
        metadata.setUrl(database.getUrl());
        metadata.setRssTenantDomain(getTenantDomainFromTenantId(database.getTenantId()));
        return metadata;
    }

    public static DatabaseUserMetaData convertToDatabaseUserMetadata(DatabaseUser user) throws
            RSSManagerException {
        DatabaseUserMetaData metadata = new DatabaseUserMetaData();
        metadata.setUsername(user.getUsername());
        metadata.setRssInstanceName(user.getRssInstanceName());
        metadata.setTenantDomain(getTenantDomainFromTenantId(user.getTenantId()));
        return metadata;
    }

    public static String composeDatabaseUrl (RSSInstance rssIns, String databaseName) {
        return rssIns.getServerURL() + "/" + databaseName;
    }

//    private static synchronized String loadFromSecureVault(String alias) {
//		if (secretResolver == null) {
//		    secretResolver = SecretResolverFactory.create((OMElement) null, false);
//		    secretResolver.init(DataSourceServiceComponent.
//		    		getSecretCallbackHandlerService().getSecretCallbackHandler());
//		}
//		return secretResolver.resolve(alias);
//	}

}
