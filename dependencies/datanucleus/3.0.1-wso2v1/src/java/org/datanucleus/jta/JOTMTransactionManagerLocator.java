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
package org.datanucleus.jta;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.util.NucleusLogger;

/**
 * Locator for the TransactionManager for JOTM.
 */
public class JOTMTransactionManagerLocator extends FactoryBasedTransactionManagerLocator
{
    Class factoryClass = null;

    /**
     * Accessor for the factory class to use for this locator.
     * @param clr ClassLoader resolver
     * @return The class
     */
    protected Class getFactoryClass(ClassLoaderResolver clr)
    {
        if (factoryClass != null)
        {
            return factoryClass;
        }

        // Set the factoryClass since it will be used by the superclass
        try
        {
            try
            {
                factoryClass = clr.classForName("org.objectweb.jotm.Current");
            }
            catch (Exception e)
            {
            }
        }
        catch (Exception e)
        {
            if (NucleusLogger.TRANSACTION.isDebugEnabled())
            {
                NucleusLogger.TRANSACTION.debug("Exception finding JOTM transaction manager. " +
                    "Probably not in a JOTM environment " + e.getMessage());
            }
        }
        return factoryClass;
    }
}