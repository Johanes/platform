/*
 * Copyright 2012 WSO2, Inc. (http://wso2.com)
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

package org.wso2.carbon.databridge.persistence.cassandra.datastore;

import org.apache.log4j.Logger;
import org.wso2.carbon.databridge.commons.Credentials;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.databridge.core.definitionstore.AbstractStreamDefinitionStore;
import org.wso2.carbon.databridge.core.exception.StreamDefinitionStoreException;
import org.wso2.carbon.databridge.persistence.cassandra.internal.util.ServiceHolder;

import java.util.Collection;

/**
 * Cassandra based Event Stream Definition store implementation
 */
public class CassandraStreamDefinitionStore extends AbstractStreamDefinitionStore {

    Logger log = Logger.getLogger(CassandraStreamDefinitionStore.class);


    public CassandraStreamDefinitionStore(){
    }

    @Override
    protected boolean removeStreamId(Credentials credentials, String streamIdKey) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean removeStreamDefinition(Credentials credentials, String streamId) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void saveStreamIdToStore(Credentials credentials, String streamIdKey, String streamId)
            throws StreamDefinitionStoreException {


        ServiceHolder.getCassandraConnector().saveStreamIdToStore(ClusterFactory.getCluster(credentials), streamIdKey, streamId);
    }

    @Override
    protected void saveStreamDefinitionToStore(Credentials credentials, String streamId, StreamDefinition streamDefinition) throws StreamDefinitionStoreException {
        ServiceHolder.getCassandraConnector().saveStreamDefinitionToStore(ClusterFactory.getCluster(credentials), streamId, streamDefinition);
    }

    @Override
    protected String getStreamIdFromStore(Credentials credentials, String streamIdKey)
            throws StreamDefinitionStoreException {
        return ServiceHolder.getCassandraConnector().getStreamIdFromStore(ClusterFactory.getCluster(credentials), streamIdKey);
    }


    @Override
    public StreamDefinition getStreamDefinitionFromStore(Credentials credentials, String streamId)
            throws StreamDefinitionStoreException {
        return ServiceHolder.getCassandraConnector().getStreamDefinitionFromStore(ClusterFactory.getCluster(credentials), streamId);
    }

    @Override
    protected Collection<StreamDefinition> getAllStreamDefinitionsFromStore(Credentials credentials) throws
            StreamDefinitionStoreException {
        return ServiceHolder.getCassandraConnector().getAllStreamDefinitionFromStore(ClusterFactory.getCluster(credentials));
    }


}
