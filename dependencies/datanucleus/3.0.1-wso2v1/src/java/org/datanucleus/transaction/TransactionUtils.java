/**********************************************************************
Copyright (c) 2007 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.transaction;

import org.datanucleus.UserTransaction;

/**
 * Utility methods relating to transactions.
 */
public class TransactionUtils
{
    /**
     * Accessor for a string name of a transaction isolation level.
     * @param isolation The isolation level (as defined by UserTransaction).
     * @return The name
     */
    public static String getNameForTransactionIsolationLevel(int isolation)
    {
        if (isolation == UserTransaction.TRANSACTION_NONE)
        {
            return "none";
        }
        else if (isolation == UserTransaction.TRANSACTION_READ_COMMITTED)
        {
            return "read-committed";
        }
        else if (isolation == UserTransaction.TRANSACTION_READ_UNCOMMITTED)
        {
            return "read-uncommitted";
        }
        else if (isolation == UserTransaction.TRANSACTION_REPEATABLE_READ)
        {
            return "repeatable-read";
        }
        else if (isolation == UserTransaction.TRANSACTION_SERIALIZABLE)
        {
            return "serializable";
        }
        else
        {
            return "UNKNOWN";
        }
    }

    /**
     * Convenience method to convert the supplied isolation level name into the
     * associated UserTransaction type number.
     * @param isolationName The name of the isolation level
     * @return Isolation level type
     */
    public static int getTransactionIsolationLevelForName(String isolationName)
    {
        if (isolationName.equalsIgnoreCase("none"))
        {
            return UserTransaction.TRANSACTION_NONE;
        }
        else if (isolationName.equalsIgnoreCase("read-committed"))
        {
            return UserTransaction.TRANSACTION_READ_COMMITTED;
        }
        else if (isolationName.equalsIgnoreCase("read-uncommitted"))
        {
            return UserTransaction.TRANSACTION_READ_UNCOMMITTED;
        }
        else if (isolationName.equalsIgnoreCase("repeatable-read"))
        {
            return UserTransaction.TRANSACTION_REPEATABLE_READ;
        }
        else if (isolationName.equalsIgnoreCase("serializable"))
        {
            return UserTransaction.TRANSACTION_SERIALIZABLE;
        }
        else
        {
            return -1;
        }
    }
}