/**********************************************************************
Copyright (c) 2006 Andy Jefferson and others. All rights reserved. 
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
package org.datanucleus.store.query;

import org.datanucleus.store.ExecutionContext;


/**
 * An object that reads result set rows and returns corresponding object(s) from them.
 * Different queries accomplish this in different ways so a query supplies a suitable ResultObjectFactory 
 * to each QueryResult when it is executed. The QueryResult only uses it to turn ResultSet rows into objects
 * and otherwise manages the ResultSet itself.
 * <p>
 * For example an implementation of this interface could return a single Persistent object per row (PersistentIDROF).
 * Another implementation could return all columns of the result set as separate objects.
 * </p>
 * @see QueryResult
 */
public interface ResultObjectFactory
{
    /**
     * Instantiates object(s) from the current row of the given result set.
     * @param ec ExecutionContext
     * @param rs The result set which will be used to convert the current row into the returned object(s).
     * @return The object(s) for this row of the ResultSet.
     */
    Object getObject(ExecutionContext ec, Object rs);
}