/**********************************************************************
Copyright (c) 2004 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.metadata;

import java.io.Serializable;

/**
 * Definition of the options for persistence-modifier of a class.
 */
public class ClassPersistenceModifier implements Serializable
{
    /** persistence-modifier="persistence-capable" */
    public static final ClassPersistenceModifier PERSISTENCE_CAPABLE = new ClassPersistenceModifier(1);

    /** persistence-modifier="persistence-aware" */
    public static final ClassPersistenceModifier PERSISTENCE_AWARE = new ClassPersistenceModifier(2);

    /** persistence-modifier="non-persistent" */
    public static final ClassPersistenceModifier NON_PERSISTENT = new ClassPersistenceModifier(3);

    /** persistence-capable|persistence-aware|non-persistent */
    private final int typeId;

    /**
     * constructor
     * @param i type id
     */
    private ClassPersistenceModifier(int i)
    {
        this.typeId = i;
    }

    public int hashCode()
    {
        return typeId;
    }

    public boolean equals(Object o)
    {
        if (o instanceof ClassPersistenceModifier)
        {
            return ((ClassPersistenceModifier)o).typeId == typeId;
        }
        return false;
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    public String toString()
    {
        switch (typeId) 
        {
            case 1 :
                return "persistence-capable";
            case 2 :
                return "persistence-aware";
            case 3 :
                return "non-persistent";
        }
        return "";
    }

    /**
     * Accessor for the Persistence Modifier id.
     * @return The id
     */
    protected int getType()
    {
        return typeId;
    }

    /**
     * Return ClassPersistenceModifier from String.
     * @param value persistence-modifier attribute value
     * @return Instance of ClassPersistenceModifier. 
     *         If value invalid, return null.
     */
    public static ClassPersistenceModifier getClassPersistenceModifier(final String value)
    {
        if (value == null)
        {
            // Default to PersistenceCapable since old files won't have this.
            return ClassPersistenceModifier.PERSISTENCE_CAPABLE;
        }
        else if (ClassPersistenceModifier.PERSISTENCE_CAPABLE.toString().equalsIgnoreCase(value))
        {
            return ClassPersistenceModifier.PERSISTENCE_CAPABLE;
        }
        else if (ClassPersistenceModifier.PERSISTENCE_AWARE.toString().equalsIgnoreCase(value))
        {
            return ClassPersistenceModifier.PERSISTENCE_AWARE;
        }
        else if (ClassPersistenceModifier.NON_PERSISTENT.toString().equalsIgnoreCase(value))
        {
            return ClassPersistenceModifier.NON_PERSISTENT;
        }
        return null;
    }
}