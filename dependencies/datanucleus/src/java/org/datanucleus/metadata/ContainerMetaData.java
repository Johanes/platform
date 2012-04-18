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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;

import org.datanucleus.ClassLoaderResolver;

/**
 * Representation of the Meta-Data for a container.
 * This is subclassed by Array, Collection, and Map.
 */
public class ContainerMetaData extends MetaData
{
    /** Whether this container allows nulls to be stored (as elements/keys/values). */
    Boolean allowNulls = null;

    /**
     * Constructor.
     */
    public ContainerMetaData()
    {
    }

    /**
     * Copy constructor.
     * @param contmd Container metadata to copy
     */
    public ContainerMetaData(ContainerMetaData contmd)
    {
        super(null, contmd);
    }

    /**
     * Method to populate any defaults, and check the validity of the MetaData.
     * @param clr ClassLoaderResolver to use for any loading operations
     * @param primary the primary ClassLoader to use (or null)
     * @param mmgr MetaData manager
     */
    public void populate(ClassLoaderResolver clr, ClassLoader primary, MetaDataManager mmgr)
    {
        // Set the default for allowNulls if not set, based on the java type
        if (parent != null && parent.hasExtension("allow-nulls"))
        {
            if (parent.getValueForExtension("allow-nulls").equalsIgnoreCase("true"))
            {
                allowNulls = Boolean.TRUE;
            }
            else if (parent.getValueForExtension("allow-nulls").equalsIgnoreCase("false"))
            {
                allowNulls = Boolean.FALSE;
            }
        }

        if (allowNulls == null)
        {
            // Set default based on the container type
            Class type = ((AbstractMemberMetaData)parent).getType();
            if (type.isArray())
            {
                if (type.getComponentType().isPrimitive())
                {
                    allowNulls = Boolean.FALSE;
                }
                else
                {
                    allowNulls = Boolean.TRUE;
                }
            }
            else if (type == HashMap.class)
            {
                allowNulls = Boolean.TRUE;
            }
            else if (type == Hashtable.class)
            {
                allowNulls = Boolean.FALSE;
            }
            else if (type == HashSet.class)
            {
                allowNulls = Boolean.TRUE;
            }
            else if (type == LinkedHashSet.class)
            {
                allowNulls = Boolean.TRUE;
            }
            else if (List.class.isAssignableFrom(type))
            {
                allowNulls = Boolean.TRUE;
            }
            // TODO Extend this for other container types
        }
    }

    /**
     * Whether this container allows nulls.
     * For a collection/array this is whether there can be null elements.
     * For a map this whether there can be null keys AND values (really we ought to treat them independent, but
     * not done like that currently).
     * @return Whether nulls are allowed to be stored in the container
     */
    public Boolean allowNulls()
    {
        return allowNulls;
    }

    /**
     * Accessor for the parent field/property MetaData.
     * @return Parent metadata
     */
    public AbstractMemberMetaData getMemberMetaData()
    {
        if (parent != null)
        {
            return (AbstractMemberMetaData)parent;
        }
        return null;
    }

    /**
     * Accessor for the parent field name
     * @return Parent field name.
     */
    public String getFieldName()
    {
        if (parent != null)
        {
            return ((AbstractMemberMetaData)parent).getName();
        }
        return null;
    }
}