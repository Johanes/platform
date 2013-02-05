/**********************************************************************
Copyright (c) 2007 Erik Bengtson and others. All rights reserved. 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. 

Contributors:
    ...
**********************************************************************/
package org.datanucleus.store.connection;

import java.util.Map;

/**
 * Factory for connections to the datastore.
 * To be implemented by all StoreManagers.
 */
public interface ConnectionFactory
{
    /** User-visible configuration property name */
    public static final String DATANUCLEUS_CONNECTION_RESOURCE_TYPE = "datanucleus.connection.resourceType";
    /** User-visible configuration property name */
    public static final String DATANUCLEUS_CONNECTION2_RESOURCE_TYPE = "datanucleus.connection2.resourceType";
    
    /** 
     * Internal property name used on ConnectionFactory instances,  
     * range of Values: JTA | RESOURCE_LOCAL
     * @see #DATANUCLEUS_CONNECTION_RESOURCE_TYPE
     * @see #DATANUCLEUS_CONNECTION2_RESOURCE_TYPE
     */
    public static final String RESOURCE_TYPE_OPTION = "resource-type";

    /**
     * Obtain a connection from the Factory. The connection will be enlisted within the {@link org.datanucleus.Transaction} 
     * associated to the <code>poolKey</code> if "enlist" is set to true.
     * @param poolKey the pool that is bound the connection during its lifecycle (or null)
     * @param options Any options for then creating the connection
     * @return the {@link org.datanucleus.store.connection.ManagedConnection}
     */
    ManagedConnection getConnection(Object poolKey, org.datanucleus.Transaction transaction, Map options);

    /**
     * Create the ManagedConnection. 
     * Only used by ConnectionManager so do not call this.
     * @param poolKey the pool that is bound the connection during its lifecycle (if any)
     * @param transactionOptions the Transaction options this connection will be enlisted to, null if non existent
     * @return The ManagedConnection.
     */
    ManagedConnection createManagedConnection(Object poolKey, Map transactionOptions);

    /**
     * Release any resources that have been allocated.
     */
    void close();
}