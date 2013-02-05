/**********************************************************************
Copyright (c) 2004 Erik Bengtson and others. All rights reserved.
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
package org.datanucleus.store.exceptions;

import org.datanucleus.exceptions.NucleusException;

/**
 * Exception thrown when an pc instance instance is not yet flushed to the datastore, but it was expected to already be.
 *
 * @version $Revision: 1.6 $ 
 */
public class NotYetFlushedException extends NucleusException
{
    private final Object pc;
    
    /**
     * Constructs a too-many-indices exception.
     * @param pc The PersistenceCapable
     */
    public NotYetFlushedException(Object pc)
    {
        // TODO Localise this message
        super("not yet flushed");
        this.pc = pc;
    }
    
    /**
     * @return Returns the pc.
     */
    public Object getPersistable()
    {
        return pc;
    }
}