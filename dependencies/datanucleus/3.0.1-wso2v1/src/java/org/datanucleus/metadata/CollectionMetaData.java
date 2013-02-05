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
2004 Erik Bengtson - add dependent elements
    ...
**********************************************************************/
package org.datanucleus.metadata;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Set;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.exceptions.ClassNotResolvedException;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * Representation of the MetaData of a collection.
 */
public class CollectionMetaData extends ContainerMetaData
{
    /** Representation of the element of the collection. */
    protected ContainerComponent element;

    /**
     * Constructor to create a copy of the passed metadata.
     * @param collmd The metadata to copy
     */
    public CollectionMetaData(CollectionMetaData collmd)
    {
        super(collmd);
        element = new ContainerComponent();
        element.embedded = collmd.element.embedded;
        element.serialized = collmd.element.serialized;
        element.dependent = collmd.element.dependent;
        element.type = collmd.element.type;
        element.classMetaData = collmd.element.classMetaData;
    }

    /**
     * Default constructor. Set the fields using setters, before populate().
     */
    public CollectionMetaData()
    {
        element = new ContainerComponent();
    }

    /**
     * Method to populate any defaults, and check the validity of the MetaData.
     * @param clr ClassLoaderResolver to use for any loading operations
     * @param primary the primary ClassLoader to use (or null)
     * @param mmgr MetaData manager
     */
    public void populate(ClassLoaderResolver clr, ClassLoader primary, MetaDataManager mmgr)
    {
        AbstractMemberMetaData mmd = (AbstractMemberMetaData)parent;
        if (!StringUtils.isWhitespace(element.type) && element.type.indexOf(',') > 0)
        {
            throw new InvalidMetaDataException(LOCALISER, "044131", mmd.getName(), mmd.getClassName());
        }

        // Make sure the type in "element" is set
        element.populate(((AbstractMemberMetaData)parent).getAbstractClassMetaData().getPackageName(), 
            clr, primary, mmgr);

        // Check the field type and see if it is castable to a Collection
        Class field_type = getMemberMetaData().getType();
        if (!java.util.Collection.class.isAssignableFrom(field_type))
        {
            throw new InvalidMetaDataException(LOCALISER, "044132",
                    getFieldName(), getMemberMetaData().getClassName(false));
        }

        // "element-type"
        if (element.type == null)
        {
            throw new InvalidMetaDataException(LOCALISER, "044133",
                    getFieldName(), getMemberMetaData().getClassName(false));
        }

        // Check that the element type exists
        Class elementTypeClass = null;
        try
        {
            elementTypeClass = clr.classForName(element.type, primary);
        }
        catch (ClassNotResolvedException cnre)
        {
            throw new InvalidMetaDataException(LOCALISER, "044134",
                getFieldName(),getMemberMetaData().getClassName(false), element.type);
        }

        if (!elementTypeClass.getName().equals(element.type))
        {
            // The element-type has been resolved from what was specified in the MetaData - update to the fully-qualified name
            NucleusLogger.METADATA.info(LOCALISER.msg("044135", getFieldName(), getMemberMetaData().getClassName(false), 
                element.type, elementTypeClass.getName()));
            element.type = elementTypeClass.getName();
        }

		// "embedded-element"
        ApiAdapter api = mmgr.getApiAdapter();
        if (element.embedded == null)
        {
            // Assign default for "embedded-element" based on 18.13.1 of JDO 2 spec
            // Note : this fails when using in the enhancer since not yet PC
            if (mmgr.getNucleusContext().getTypeManager().isDefaultEmbeddedType(elementTypeClass))
            {
                element.embedded = Boolean.TRUE;
            }
            else if (api.isPersistable(elementTypeClass) ||
                Object.class.isAssignableFrom(elementTypeClass) ||
                elementTypeClass.isInterface())
            {
                element.embedded = Boolean.FALSE;
            }
            else
            {
                element.embedded = Boolean.TRUE;
            }
        }
        if (Boolean.FALSE.equals(element.embedded))
        {
            // If the user has set a non-PC/non-Interface as not embedded, correct it since not supported.
            // Note : this fails when using in the enhancer since not yet PC
            if (!api.isPersistable(elementTypeClass) && !elementTypeClass.isInterface() &&
                elementTypeClass != java.lang.Object.class)
            {
                element.embedded = Boolean.TRUE;
            }
        }

        ElementMetaData elemmd = ((AbstractMemberMetaData)parent).getElementMetaData();
        if (elemmd != null && elemmd.getEmbeddedMetaData() != null)
        {
            element.embedded = Boolean.TRUE;
        }

        if (Boolean.TRUE.equals(element.dependent))
        {
            // If the user has set a non-PC/non-reference as dependent, correct it since not valid.
            // Note : this fails when using in the enhancer since not yet PC
            if (!api.isPersistable(elementTypeClass) && !elementTypeClass.isInterface() &&
                elementTypeClass != java.lang.Object.class)
            {
                element.dependent = Boolean.FALSE;
            }
        }

        // Keep a reference to the MetaData for the element
        element.classMetaData = mmgr.getMetaDataForClassInternal(elementTypeClass, clr);

        // Make sure anything in the superclass is populated too
        super.populate(clr, primary, mmgr);

        setPopulated();
    }

    /**
     * Accessor for the element-type tag value.
     * This can contain comma-separated values.
     * @return element-type tag value
     */
    public String getElementType()
    {
        return element.type;
    }

    public boolean elementIsPersistent()
    {
        return element.classMetaData != null;
    }

    /**
     * Convenience accessor for the Element ClassMetaData.
     * @param clr ClassLoader resolver (in case we need to initialise it)
     * @param mmgr MetaData manager
     * @return element ClassMetaData
     */
    public AbstractClassMetaData getElementClassMetaData(final ClassLoaderResolver clr, final MetaDataManager mmgr)
    {
        if (element.classMetaData != null && !element.classMetaData.isInitialised())
        {
            // Do as PrivilegedAction since uses reflection
            // [JDOAdapter.isValidPrimaryKeyClass calls reflective methods]
            AccessController.doPrivileged(new PrivilegedAction()
            {
                public Object run()
                {
                    element.classMetaData.initialise(clr, mmgr);
                    return null;
                }
            });
        }
        return element.classMetaData;
    }

    /**
     * Accessor for the embedded-element tag value
     * @return embedded-element tag value
     */
    public boolean isEmbeddedElement()
    {
        if (element.embedded == null)
        {
            return false;
        }
        else
        {
            return element.embedded.booleanValue();
        }
    }

    /**
     * Accessor for The dependent-element attribute indicates that the
     * collection's element contains a reference that is to be deleted if the
     * referring instance is deleted.
     * 
     * @return dependent-element tag value
     */
    public boolean isDependentElement()
    {
        if (element.dependent == null)
        {
            return false;
        }
        else if (element.classMetaData == null)
        {
            return false;
        }
        else
        {
            return element.dependent.booleanValue();
        }
    }

    /**
     * Accessor for the serialized-element tag value
     * @return serialized-element tag value
     */
    public boolean isSerializedElement()
    {
        if (element.serialized == null)
        {
            return false;
        }
        else
        {
            return element.serialized.booleanValue();
        }
    }

    public CollectionMetaData setElementType(String type)
    {
        element.setType(type);
        return this;
    }

    public CollectionMetaData setEmbeddedElement(String embedded)
    {
        element.setEmbedded(embedded);
        return this;
    }

    public CollectionMetaData setEmbeddedElement(boolean embedded)
    {
        element.setEmbedded(embedded);
        return this;
    }

    public CollectionMetaData setSerializedElement(String serialized)
    {
        element.setSerialized(serialized);
        return this;
    }

    public CollectionMetaData setSerializedElement(boolean serialized)
    {
        element.setSerialized(serialized);
        return this;
    }

    public CollectionMetaData setDependentElement(String dependent)
    {
        element.setDependent(dependent);
        return this;
    }

    public CollectionMetaData setDependentElement(boolean dependent)
    {
        element.setDependent(dependent);
        return this;
    }

    // ------------------------------- Utilities -------------------------------

    /**
     * Accessor for all ClassMetaData referenced by this array.
     * @param orderedCMDs List of ordered ClassMetaData objects (added to).
     * @param referencedCMDs Set of all ClassMetaData objects (added to).
     * @param clr the ClassLoaderResolver
     * @param mmgr MetaData manager
     */
    void getReferencedClassMetaData(final List orderedCMDs, final Set referencedCMDs,
            final ClassLoaderResolver clr, final MetaDataManager mmgr)
    { 
        AbstractClassMetaData element_cmd = mmgr.getMetaDataForClass(element.type, clr);
        if (element_cmd != null)
        {
            element_cmd.getReferencedClassMetaData(orderedCMDs, referencedCMDs, clr, mmgr);
        }
    }

    /**
     * Returns a string representation of the object.
     * @param prefix prefix string
     * @param indent indent string
     * @return a string representation of the object.
     */
    public String toString(String prefix,String indent)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(prefix).append("<collection element-type=\"").append(element.type).append("\"");
        if (element.embedded != null)
        {
            sb.append(" embedded-element=\"").append(element.embedded).append("\"");
        }
        if (element.dependent != null)
        {
            sb.append(" dependent-element=\"").append(element.dependent).append("\"");
        }
        if (element.serialized != null)
        {
            sb.append(" serialized-element=\"").append(element.serialized).append("\"");
        }
        sb.append(">\n");

        // Add extensions
        sb.append(super.toString(prefix + indent,indent)); 
 
        sb.append(prefix).append("</collection>\n");
        return sb.toString();
    }
}