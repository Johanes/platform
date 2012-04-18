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
2007 Xuan Baldauf - Make error message "023011" a little bit more verbose
    ...
**********************************************************************/
package org.datanucleus.store.types.sco;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusObjectNotFoundException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.state.FetchPlanState;
import org.datanucleus.store.BackedSCOStoreManager;
import org.datanucleus.store.ExecutionContext;
import org.datanucleus.store.FieldValues;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.StoreManager;
import org.datanucleus.store.scostore.CollectionStore;
import org.datanucleus.store.scostore.MapStore;
import org.datanucleus.store.scostore.SetStore;
import org.datanucleus.store.types.TypeManager;
import org.datanucleus.util.ClassUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * Collection of utilities for second class wrappers and objects.
 */
public class SCOUtils
{
    /** Localiser for messages. */
    private static final Localiser LOCALISER = Localiser.getInstance(
        "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /**
     * Method to create a new SCO wrapper for a SCO type.
     * The SCO wrapper will be appropriate for the passed value (which represents the instantiated type of the field)
     * unless it is null when the wrapper will be appropriate for the declared type of the field.
     * While the "instantiated type" and the type of "value" should be the same when value is non-null, there are
     * situations where we need to create a List based collection yet have no value so pass in the declaredType
     * as Collection, instantiatedType as ArrayList, and value as null.
     * @param ownerOP State Manager for the owning object
     * @param mmd The Field MetaData for the related field.
     * @param declaredType The class of the object
     * @param instantiatedType Instantiated type for the field if known
     * @param value The value we are wrapping if known
     * @param forInsert Whether the SCO needs inserting in the datastore with this value
     * @param forUpdate Whether the SCO needs updating in the datastore with this value
     * @param replaceField Whether to replace the field with this value
     * @return The Second-Class Object
     * @throws NucleusUserException if an error occurred when creating the SCO instance
     */
    public static SCO newSCOInstance(ObjectProvider ownerOP, AbstractMemberMetaData mmd, 
            Class declaredType, Class instantiatedType, Object value, boolean forInsert, boolean forUpdate,
            boolean replaceField)
    {
        if (!mmd.getType().isAssignableFrom(declaredType))
        {
            throw new NucleusUserException(LOCALISER.msg("023010",
                declaredType.getName(), mmd.getName(), mmd.getType()));
        }

        // Check if the passed in value is a wrapper type
        TypeManager typeMgr = ownerOP.getExecutionContext().getNucleusContext().getTypeManager();
        if (value != null && typeMgr.isSecondClassWrapper(value.getClass().getName()))
        {
            // The passed in value is a wrapper type already, so just return it!
            if (replaceField)
            {
                // Replace the field with this value
                ownerOP.replaceField(mmd.getAbsoluteFieldNumber(), value);
            }
            return (SCO)value;
        }

        String typeName = declaredType.getName();
        if (instantiatedType != null)
        {
            // Use instantiated type if available
            typeName = instantiatedType.getName();
        }
        if (value != null)
        {
            // If we have a current value, use the actual type to define the wrapper type
            typeName = value.getClass().getName();
        }

        // Find the SCO wrapper type most suitable
        StoreManager storeMgr = ownerOP.getExecutionContext().getStoreManager();
        boolean fullWrapper = (storeMgr instanceof BackedSCOStoreManager);
        Class wrapperType = null;
        if (fullWrapper)
        {
            wrapperType = SCOUtils.getBackedWrapperTypeForType(declaredType, instantiatedType, typeName, typeMgr);
        }
        else
        {
            wrapperType = SCOUtils.getSimpleWrapperTypeForType(declaredType, instantiatedType, typeName, typeMgr);
        }
        if (wrapperType == null)
        {
            throw new NucleusUserException(LOCALISER.msg("023011", 
                declaredType.getName(), StringUtils.toJVMIDString(value), mmd.getFullFieldName()));
        }

        // Create the SCO wrapper
        SCO sco = null;
        try
        {
            sco = createSCOWrapper(wrapperType, ownerOP, mmd, replaceField, value, forInsert, forUpdate);
        }
        catch (UnsupportedOperationException uoe)
        {
            // Can't create backing store? so try simple wrapper
            if (fullWrapper)
            {
                NucleusLogger.PERSISTENCE.warn("Creation of backed wrapper for " + mmd.getFullFieldName() + " unsupported, so trying simple wrapper");
                wrapperType = SCOUtils.getSimpleWrapperTypeForType(declaredType, instantiatedType, typeName, typeMgr);
                sco = createSCOWrapper(wrapperType, ownerOP, mmd, replaceField, value, forInsert, forUpdate);
            }
            else
            {
                throw uoe;
            }
        }

        return sco;
    }

    /**
     * Convenience method to return the backed wrapper type for the field definition.
     * Wrapper is null if no backed wrapper is defined for the type.
     * @param declaredType Declared type of the field
     * @param instantiatedType Instantiated type of the field
     * @param typeName Type name to try first
     * @param typeMgr The type manager
     * @return The wrapper type
     */
    private static Class getBackedWrapperTypeForType(Class declaredType, Class instantiatedType, String typeName, 
            TypeManager typeMgr)
    {
        Class wrapperType = typeMgr.getWrappedTypeBackedForType(typeName);
        if (wrapperType == null)
        {
            // typeName not supported directly (no SCO wrapper for the precise type)
            if (instantiatedType != null)
            {
                // Try the instantiated type
                wrapperType = typeMgr.getWrappedTypeBackedForType(instantiatedType.getName());
            }
            if (wrapperType == null)
            {
                // Try the declared type
                wrapperType = typeMgr.getWrappedTypeBackedForType(declaredType.getName());
            }
        }
        return wrapperType;
    }

    /**
     * Convenience method to return the simple wrapper type for the field definition.
     * Wrapper is null if no simple wrapper is defined for the type.
     * @param declaredType Declared type of the field
     * @param instantiatedType Instantiated type of the field
     * @param typeName Type name to try first
     * @param typeMgr The type manager
     * @return The wrapper type
     */
    private static Class getSimpleWrapperTypeForType(Class declaredType, Class instantiatedType, String typeName, 
            TypeManager typeMgr)
    {
        Class wrapperType = typeMgr.getWrapperTypeForType(typeName);
        if (wrapperType == null)
        {
            // typeName not supported directly (no SCO wrapper for the precise type)
            if (instantiatedType != null)
            {
                // Try the instantiated type
                wrapperType = typeMgr.getWrapperTypeForType(instantiatedType.getName());
            }
            if (wrapperType == null)
            {
                // Try the declared type
                wrapperType = typeMgr.getWrapperTypeForType(declaredType.getName());
            }
        }
        return wrapperType;
    }

    /**
     * Convenience method to create an instance of the wrapper class.
     * @param wrapperType Wrapper type
     * @param ownerOP ObjectProvider for the object with the field being replaced with this wrapper
     * @param mmd Metadata for the field/property
     * @param replaceField Whether we should replace the field in the owner with this wrapper
     * @param fieldValue The value of the field (to wrap)
     * @param forInsert Whether this value is to be inserted in the datastore
     * @param forUpdate Whether this value is to be updated in the datastore
     * @return The wrapped value
     */
    private static SCO createSCOWrapper(Class wrapperType, ObjectProvider ownerOP, AbstractMemberMetaData mmd,
            boolean replaceField, Object fieldValue, boolean forInsert, boolean forUpdate)
    {
        // Create the SCO wrapper
        SCO sco = (SCO) ClassUtils.newInstance(wrapperType, 
            new Class[]{ObjectProvider.class, String.class}, 
            new Object[]{ownerOP, mmd.getName()});

        if (replaceField)
        {
            // Replace the field with this value before initialising it
            ownerOP.replaceField(mmd.getAbsoluteFieldNumber(), sco);
        }

        // Initialise the SCO for use
        if (fieldValue != null)
        {
            // Apply the existing value
            sco.initialise(fieldValue, forInsert, forUpdate);
        }
        else
        {
            // Just create it empty and load from the datastore
            sco.initialise();
        }

        return sco;
    }

    /**
     * Utility to generate a message representing the SCO container wrapper and its capabilities.
     * @param ownerOP ObjectProvider for the owner
     * @param fieldName Field with the container
     * @param cont The SCOContainer
     * @param useCache Whether to use caching of values in the container
     * @param queued Whether operations are queued in the wrapper
     * @param allowNulls Whether to allow nulls
     * @param lazyLoading Whether to use lazy loading in the wrapper
     * @return The String
     */
    public static String getContainerInfoMessage(ObjectProvider ownerOP, String fieldName, SCOContainer cont,
            boolean useCache, boolean queued, boolean allowNulls, boolean lazyLoading)
    {
        String msg = LOCALISER.msg("023004",
            ownerOP.toPrintableID(), fieldName,
            cont.getClass().getName(),
            "[cache-values=" + useCache +
            ", lazy-loading=" + SCOUtils.useCachedLazyLoading(ownerOP, fieldName) +
            ", queued-operations=" + queued +
            ", allow-nulls=" + allowNulls + "]");
        return msg;
    }

    /**
     * Convenience method to generate a message containing the options of this SCO wrapper.
     * @param useCache Whether to cache the value in the wrapper (and not go to the datastore)
     * @param queued Whether it supports queueing of updates
     * @param allowNulls Whether it allows null entries
     * @param lazyLoading Whether it is lazy loaded
     * @return the message
     */
    public static String getSCOWrapperOptionsMessage(boolean useCache, boolean queued, boolean allowNulls, boolean lazyLoading)
    {
        StringBuffer str = new StringBuffer();
        if (useCache)
        {
            str.append("cached");
        }
        if (lazyLoading)
        {
            if (str.length() > 0)
            {
                str.append(",");
            }
            str.append("lazy-loaded");
        }
        if (queued)
        {
            if (str.length() > 0)
            {
                str.append(",");
            }
            str.append("queued");
        }
        if (allowNulls)
        {
            if (str.length() > 0)
            {
                str.append(",");
            }
            str.append("allowNulls");
        }
        return str.toString();
    }

    /**
     * Utility to return whether or not to allow nulls in the container for the specified field.
     * @param defaultValue Default value for the container
     * @param mmd MetaData for the field/property
     * @return Whether to allow nulls
     */
    public static boolean allowNullsInContainer(boolean defaultValue, AbstractMemberMetaData mmd)
    {
        if (mmd.getContainer() == null)
        {
            return defaultValue;
        }
        else if (Boolean.TRUE.equals(mmd.getContainer().allowNulls()))
        {
            return true;
        }
        else if (Boolean.FALSE.equals(mmd.getContainer().allowNulls()))
        {
            return false;
        }
        return defaultValue;
    }

    /**
     * Utility to return whether or not to use the container cache for the
     * collection/map for the passed ObjectProvider SCO.
     * @param ownerOP The ObjectProvider for the SCO field
     * @param fieldName Name of the field.
     * @return Whether to use the cache.
     */
    public static boolean useContainerCache(ObjectProvider ownerOP, String fieldName)
    {
        if (ownerOP == null)
        {
            return false;
        }

        // Get global value for PMF
        boolean useCache = ownerOP.getExecutionContext().getNucleusContext().getPersistenceConfiguration().getBooleanProperty("datanucleus.cache.collections");

        AbstractMemberMetaData fmd = ownerOP.getExecutionContext().getMetaDataManager().getMetaDataForMember(ownerOP.getObject().getClass(), 
            ownerOP.getExecutionContext().getClassLoaderResolver(), fieldName);
        if (fmd.getOrderMetaData() != null && !fmd.getOrderMetaData().isIndexedList())
        {
            // "Ordered Lists" have to use caching since most List operations are impossible without indexing
            useCache = true;
        }
        else if (fmd.getContainer() != null && fmd.getContainer().hasExtension("cache"))
        {
            // User has marked the field caching policy
            useCache = Boolean.parseBoolean(fmd.getContainer().getValueForExtension("cache"));
        }

        return useCache;
    }

    /**
     * Accessor for whether the use lazy loading when caching the collection.
     * @param ownerOP ObjectProvider of the owning object
     * @param fieldName Name of the collection/map field
     * @return Whether to use lazy loading when caching the collection
     */
    public static boolean useCachedLazyLoading(ObjectProvider ownerOP, String fieldName)
    {
        if (ownerOP == null)
        {
            return false;
        }

        boolean lazy = false;

        AbstractClassMetaData cmd = ownerOP.getClassMetaData();
        AbstractMemberMetaData fmd = cmd.getMetaDataForMember(fieldName);
        Boolean lazyCollections = ownerOP.getExecutionContext().getNucleusContext().getPersistenceConfiguration().getBooleanObjectProperty("datanucleus.cache.collections.lazy");
        if (lazyCollections != null)
        {
            // Global setting for PMF
            lazy = lazyCollections.booleanValue();
        }
        else if (fmd.getContainer() != null && fmd.getContainer().hasExtension("cache-lazy-loading"))
        {
            // Check if this container has a MetaData value defined
            lazy = Boolean.parseBoolean(fmd.getContainer().getValueForExtension("cache-lazy-loading"));
        }
        else
        {
            // Check if this SCO is in the current FetchPlan
            boolean inFP = false;
            int[] fpFields = ownerOP.getExecutionContext().getFetchPlan().getFetchPlanForClass(cmd).getMemberNumbers();
            int fieldNo = fmd.getAbsoluteFieldNumber();
            if (fpFields != null && fpFields.length > 0)
            {
                for (int i=0;i<fpFields.length;i++)
                {
                    if (fpFields[i] == fieldNo)
                    {
                        inFP = true;
                        break;
                    }
                }
            }
            // Default to lazy loading when not in FetchPlan, and non-lazy when in FetchPlan
            lazy = !inFP;
        }

        return lazy;
    }

    /**
     * Convenience method to return if a collection field has elements without their own identity.
     * Checks if the elements are embedded in a join table, or in the main table, or serialised.
     * @param mmd MetaData for the field
     * @return Whether the elements have their own identity or not
     */
    public static boolean collectionHasElementsWithoutIdentity(AbstractMemberMetaData mmd)
    {
        boolean elementsWithoutIdentity = false;
        if (mmd.isSerialized())
        {
            // Elements serialised into main table
            elementsWithoutIdentity = true;
        }
        else if (mmd.getElementMetaData() != null && mmd.getElementMetaData().getEmbeddedMetaData() != null && mmd.getJoinMetaData() != null)
        {
            // Elements embedded in join table using embedded mapping
            elementsWithoutIdentity = true;
        }
        else if (mmd.getCollection() != null && mmd.getCollection().isEmbeddedElement())
        {
            // Elements are embedded (either serialised, or embedded in join table)
            elementsWithoutIdentity = true;
        }

        return elementsWithoutIdentity;
    }

    /**
     * Convenience method to return if a map field has keys without their own identity.
     * Checks if the keys are embedded in a join table, or in the main table, or serialised.
     * @param fmd MetaData for the field
     * @return Whether the keys have their own identity or not
     */
    public static boolean mapHasKeysWithoutIdentity(AbstractMemberMetaData fmd)
    {
        boolean keysWithoutIdentity = false;
        if (fmd.isSerialized())
        {
            // Keys (and values) serialised into main table
            keysWithoutIdentity = true;
        }
        else if (fmd.getKeyMetaData() != null && fmd.getKeyMetaData().getEmbeddedMetaData() != null && fmd.getJoinMetaData() != null)
        {
            // Keys embedded in join table using embedded mapping
            keysWithoutIdentity = true;
        }
        else if (fmd.getMap() != null && fmd.getMap().isEmbeddedKey())
        {
            // Keys are embedded (either serialised, or embedded in join table)
            keysWithoutIdentity = true;
        }

        return keysWithoutIdentity;
    }

    /**
     * Convenience method to return if a map field has values without their own identity.
     * Checks if the values are embedded in a join table, or in the main table, or serialised.
     * @param fmd MetaData for the field
     * @return Whether the values have their own identity or not
     */
    public static boolean mapHasValuesWithoutIdentity(AbstractMemberMetaData fmd)
    {
        boolean valuesWithoutIdentity = false;
        if (fmd.isSerialized())
        {
            // Values (and keys) serialised into main table
            valuesWithoutIdentity = true;
        }
        else if (fmd.getValueMetaData() != null && fmd.getValueMetaData().getEmbeddedMetaData() != null && fmd.getJoinMetaData() != null)
        {
            // Values embedded in join table using embedded mapping
            valuesWithoutIdentity = true;
        }
        else if (fmd.getMap() != null && fmd.getMap().isEmbeddedValue())
        {
            // Values are embedded (either serialised, or embedded in join table)
            valuesWithoutIdentity = true;
        }

        return valuesWithoutIdentity;
    }

    /**
     * Convenience method to return if a collection field has the elements serialised into the
     * table of the field as a single BLOB.
     * This is really for use within an RDBMS context.
     * @param fmd MetaData for the field
     * @return Whether the elements are serialised (either explicitly or implicitly)
     */
    public static boolean collectionHasSerialisedElements(AbstractMemberMetaData fmd)
    {
        boolean serialised = fmd.isSerialized();
        if (fmd.getCollection() != null && fmd.getCollection().isEmbeddedElement() && 
            fmd.getJoinMetaData() == null)
        {
            // Elements are embedded but no join table so we serialise
            serialised = true;
        }

        return serialised;
    }

    /**
     * Convenience method to return if an array field has the elements stored into the
     * table of the field as a single (BLOB) column.
     * @param fmd MetaData for the field
     * @param mmgr MetaData manager
     * @return Whether the elements are stored in a single column
     */
    public static boolean arrayIsStoredInSingleColumn(AbstractMemberMetaData fmd, MetaDataManager mmgr)
    {
        boolean singleColumn = fmd.isSerialized();
        if (!singleColumn && fmd.getArray() != null && fmd.getJoinMetaData() == null)
        {
            if (fmd.getArray().isEmbeddedElement())
            {
                // Elements are embedded but no join table so we store in a single column
                singleColumn = true;
            }

            Class elementClass = fmd.getType().getComponentType();
            ApiAdapter api = mmgr.getApiAdapter();
            if (!elementClass.isInterface() && !api.isPersistable(elementClass))
            {
                // Array of non-PC with no join table  so store in single column of main table
                singleColumn = true;
            }
        }

        return singleColumn;
    }

    /**
     * Convenience method to return if a map field has the keys/values serialised.
     * This is really for use within an RDBMS context.
     * @param fmd MetaData for the field
     * @return Whether the keys and values are serialised (either explicitly or implicitly)
     */
    public static boolean mapHasSerialisedKeysAndValues(AbstractMemberMetaData fmd)
    {
        boolean inverseKeyField = false;
        if (fmd.getKeyMetaData() != null && fmd.getKeyMetaData().getMappedBy() != null)
        {
            inverseKeyField = true;
        }
        boolean inverseValueField = false;
        if (fmd.getValueMetaData() != null && fmd.getValueMetaData().getMappedBy() != null)
        {
            inverseValueField = true;
        }

        boolean serialised = fmd.isSerialized();
        if (fmd.getMap() != null && fmd.getJoinMetaData() == null &&
            (fmd.getMap().isEmbeddedKey() && fmd.getMap().isEmbeddedValue()) &&
            !inverseKeyField && !inverseValueField)
        {
            // Keys AND values are embedded but no join table so we serialise the whole map
            // Note that we explicitly excluded the 1-N Map with the key stored in the value
            serialised = true;
        }

        return serialised;
    }

    /**
     * Convenience method to update a collection to contain the elements in another collection.
     * Performs the updates by calling the necessary add(), remove() methods just for the
     * elements that have changed. Allows for some elements in one collection being attached
     * and some being detached (so having same id, but different state)
     * @param api API Adapter
     * @param coll The collection to update
     * @param newColl The new collection whose elements we need in "coll"
     */
    public static void updateCollectionWithCollection(ApiAdapter api, Collection coll, Collection newColl)
    {
        if (coll == null)
        {
            return;
        }

        if (newColl == null)
        {
            coll.clear();
            return;
        }

        // Remove all elements no longer in the Collection
        Iterator iter = coll.iterator();
        while (iter.hasNext())
        {
            Object element = iter.next();
            if (api.isPersistable(element))
            {
                Object id = api.getIdForObject(element);

                if (id != null)
                {
                    // Element has an id so compare the id
                    boolean present = false;
                    Iterator newIter = newColl.iterator();
                    while (newIter.hasNext())
                    {
                        Object newElement = newIter.next();
                        Object newId = api.getIdForObject(newElement);

                        if (id.equals(newId))
                        {
                            present = true;
                            break;
                        }
                    }

                    if (!present)
                    {
                        iter.remove();
                    }
                }
                else
                {
                    if (!newColl.contains(element))
                    {
                        iter.remove();
                    }
                }
            }
            else
            {
                if (!newColl.contains(element))
                {
                    iter.remove();
                }
            }
        }

        // Add all new elements
        Iterator newIter = newColl.iterator();
        while (newIter.hasNext())
        {
            Object newElement = newIter.next();

            if (api.isPersistable(newElement))
            {
                Object newId = api.getIdForObject(newElement);
                if (newId != null)
                {
                    boolean present = false;
                    iter = coll.iterator();
                    while (iter.hasNext())
                    {
                        Object element = iter.next();
                        Object id = api.getIdForObject(element);
                        if (newId.equals(id))
                        {
                            present = true;
                            break;
                        }
                    }

                    if (!present)
                    {
                        coll.add(newElement);
                    }
                }
                else
                {
                    if (!coll.contains(newElement))
                    {
                        coll.add(newElement);
                    }
                }
            }
            else
            {
                if (!coll.contains(newElement))
                {
                    coll.add(newElement);
                }
            }
        }
    }

    /**
     * Convenience method for use by Collection/Set/HashSet attachCopy methods to
     * remove any no-longer-needed elements from the collection (for when elements are removed
     * when detached).
     * @param coll The current (attached) collection
     * @param elements The collection of (attached) elements needed.
     * @param elementsWithoutId Whether the elements have no identity
     * @return If the Collection was updated
     */
    public static boolean attachRemoveDeletedElements(ApiAdapter api, Collection coll, Collection elements,
            boolean elementsWithoutId)
    {
        boolean updated = false;

        // Delete any elements that are no longer in the collection
        Iterator attachedIter = coll.iterator();
        while (attachedIter.hasNext())
        {
            Object currentElem = attachedIter.next();
            Object currentElemId = api.getIdForObject(currentElem);
            Iterator desiredIter = elements.iterator();
            boolean contained = false;
            if (elementsWithoutId)
            {
                contained = elements.contains(currentElem);
            }
            else
            {
                while (desiredIter.hasNext())
                {
                    Object desiredElem = desiredIter.next();
                    if (currentElemId.equals(api.getIdForObject(desiredElem)))
                    {
                        contained = true;
                        break;
                    }
                }
            }
            if (!contained)
            {
                // No longer present so remove it
                attachedIter.remove();
                updated = true;
            }
        }
        return updated;
    }

    /**
     * Convenience method for use by Collection/Set/HashSet attachCopy methods to
     * add any new elements (added whilst detached) to the collection.
     * @param coll The current (attached) collection
     * @param elements The collection of (attached) elements needed.
     * @param elementsWithoutId Whether the elements have no identity
     * @return If the Collection was updated
     */
    public static boolean attachAddNewElements(ApiAdapter api, Collection coll, Collection elements,
            boolean elementsWithoutId)
    {
        boolean updated = false;

        // Add any new elements
        Iterator elementsIter = elements.iterator();
        while (elementsIter.hasNext())
        {
            Object element = elementsIter.next();
            Object elemId = api.getIdForObject(element);
            boolean contained = false;
            if (elementsWithoutId)
            {
                contained = coll.contains(element);
            }
            else
            {
                Iterator collIter = coll.iterator();
                while (collIter.hasNext())
                {
                    Object currentElem = collIter.next();
                    if (api.getIdForObject(currentElem).equals(elemId))
                    {
                        contained = true;
                        break;
                    }
                }
            }
            if (!contained)
            {
                // Not present so add it
                coll.add(element);
                updated = true;
            }
        }
        return updated;
    }

    /**
     * Convenience method for use by Collection/Set/HashSet attachCopy methods to
     * update the passed (attached) collection using the (attached) elements passed.
     * @param coll The current (attached) collection
     * @param elements The collection of (attached) elements needed.
     * @return If the Collection was updated
     */
    public static boolean updateCollectionWithCollectionElements(Collection coll, Collection elements)
    {
        boolean updated = false;

        // Delete any elements that are no longer in the collection
        Iterator attachedIter = coll.iterator();
        while (attachedIter.hasNext())
        {
            Object attachedElement = attachedIter.next();
            if (!elements.contains(attachedElement))
            {
                // No longer present so remove it
                attachedIter.remove();
                updated = true;
            }
        }

        // Add any new elements
        Iterator elementsIter = elements.iterator();
        while (elementsIter.hasNext())
        {
            Object element = elementsIter.next();
            if (!coll.contains(element))
            {
                // Not present so add it
                coll.add(element);
                updated = true;
            }
        }
        return updated;
    }

    /**
     * Convenience method for use by List attachCopy methods to update the
     * passed (attached) list using the (attached) list elements passed.
     * @param list The current (attached) list
     * @param elements The list of (attached) elements needed.
     * @return If the List was updated
     */
    public static boolean updateListWithListElements(List list, List elements)
    {
        boolean updated = false;

        // This method needs to take the existing list and generate a list
        // of add/remove/set/clear operations that change the list to the passed
        // elements in as efficient a way as possible. The simplest is
        // clear() then addAll()!, but if there are many objects and very little
        // has changed this would be very inefficient.
        // What we do currently is remove all elements no longer present, and then
        // add any missing elements, correcting the ordering. This can be non-optimal
        // in some situations.
        // TODO Optimise the process
        // Delete any elements that are no longer in the list
        java.util.ArrayList newCopy = new java.util.ArrayList(elements);
        Iterator attachedIter = list.iterator();
        while (attachedIter.hasNext())
        {
            Object attachedElement = attachedIter.next();
            if (!newCopy.remove(attachedElement))
            {
                // No longer present, so remove it
                attachedIter.remove();
                updated = true;
            }
        }

        // Add any new elements that have been added
        java.util.ArrayList oldCopy = new java.util.ArrayList(list);
        Iterator elementsIter = elements.iterator();
        while (elementsIter.hasNext())
        {
            Object element = elementsIter.next();
            if (!oldCopy.remove(element))
            {
                // Now present, so add it
                list.add(element);
                updated = true;
            }
        }

        // Update position of elements in the list to match the new order
        elementsIter = elements.iterator();
        int position = 0;
        while (elementsIter.hasNext())
        {
            Object element = elementsIter.next();
            Object currentElement = list.get(position);
            boolean updatePosition = false;
            if ((element == null && currentElement != null) ||
                (element != null && currentElement == null))
            {
                // Cater for null elements in the list
                updatePosition = true;
            }
            else if (element != null && currentElement != null && !currentElement.equals(element))
            {
                updatePosition = true;
            }

            if (updatePosition)
            {
                // Update the position, taking care not to have dependent-field deletes taking place
                ((SCOList)list).set(position, element, false);
                updated = true;
            }

            position++;
        }

        return updated;
    }

    /**
     * Convenience method for use by Map attachCopy methods to update the
     * passed (attached) map using the (attached) map keys/values passed.
     * @param api Api adapter
     * @param map The current (attached) map
     * @param keysValues The keys/values required
     * @return If the map was updated
     */
    public static boolean updateMapWithMapKeysValues(ApiAdapter api, Map map, Map keysValues)
    {
        boolean updated = false;

        // Take a copy of the map so we can call remove() on the map itself
        // TODO Change this to use EntrySet in the future.
        // EntrySet.iterator().remove() doesn't seem to feed through to the DB at the moment
        Map copy = new HashMap(map);

        // Delete any keys that are no longer in the Map
        Iterator attachedIter = copy.entrySet().iterator();
        while (attachedIter.hasNext())
        {
            Map.Entry entry = (Map.Entry) attachedIter.next();
            Object key = entry.getKey();
            if (!keysValues.containsKey(key))
            {
                map.remove(key);
                updated = true;
            }
        }

        // Add any new keys/values and update any changed values
        Iterator keysIter = keysValues.entrySet().iterator();
        while (keysIter.hasNext())
        {
            Map.Entry entry = (Map.Entry) keysIter.next();
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (!map.containsKey(key))
            {
                // Not present so add it
                map.put(key, keysValues.get(key));
                updated = true;
            }
            else
            {
                // Update any values
                Object oldValue = map.get(key);
                if (api.isPersistable(value) && api.getIdForObject(value) != api.getIdForObject(oldValue))
                {
                    // In case they have changed the PC for this key (different id)
                    map.put(key, value);
                }
                else
                {
                    if ((oldValue == null && value != null) || (oldValue != null && !oldValue.equals(value)))
                    {
                        map.put(key, value);
                    }
                }
            }
        }

        return updated;
    }

    /**
     * Convenience method to populate the passed delegate Map with the keys/values from
     * the associated Store.
     * <P>
     * The issue here is that we need to load the keys and values in as few calls as possible.
     * The method employed here reads in the keys (if PersistenceCapable), then the values
     * (if PersistenceCapable), and then the "entries" (ids of keys and values) so we can 
     * associate the keys to the values.
     * @param delegate The delegate
     * @param store The Store
     * @param ownerOP ObjectProvider of the owner of the map.
     */
    public static void populateMapDelegateWithStoreData(Map delegate, MapStore store, ObjectProvider ownerOP)
    {
        java.util.HashSet keys = new java.util.HashSet();

        // If we have persistable keys then load them. The keys query will pull in the key fetch plan
        // so this instantiates them in the cache
        if (!store.keysAreEmbedded() && !store.keysAreSerialised())
        {
            // Retrieve the PersistenceCapable keys
            SetStore keystore = store.keySetStore();
            Iterator keyIter = keystore.iterator(ownerOP);
            while (keyIter.hasNext())
            {
                keys.add(keyIter.next());
            }
        }

        // If we have persistable values then load them. The values query will pull in the value fetch plan
        // so this instantiates them in the cache
        java.util.HashSet values = new java.util.HashSet();
        if (!store.valuesAreEmbedded() && !store.valuesAreSerialised())
        {
            // Retrieve the PersistenceCapable values
            SetStore valuestore = store.valueSetStore();
            Iterator valueIter = valuestore.iterator(ownerOP);
            while (valueIter.hasNext())
            {
                values.add(valueIter.next());
            }
        }

        // Retrieve the entries (key-value pairs so we can associate them)
        // TODO Ultimately would like to just call this, but the entry query can omit the inheritance level
        // of a key or value
        SetStore entries = store.entrySetStore();
        Iterator entryIter = entries.iterator(ownerOP);
        while (entryIter.hasNext())
        {
            Map.Entry entry = (Map.Entry)entryIter.next();
            Object key = entry.getKey();
            Object value = entry.getValue();
            delegate.put(key, value);
        }

        if (!store.keysAreEmbedded() && !store.keysAreSerialised() && delegate.size() != keys.size())
        {
            // With Derby 10.x we can get instances where the values query returns no values yet entries is not empty
            // TODO Maybe make this throw an exception
            NucleusLogger.DATASTORE_RETRIEVE.warn("The number of Map key objects (" + keys.size() + ")" + 
                " was different to the number of entries (" + delegate.size() + ")." +
                " Likely there is a bug in your datastore");
        }

        if (!store.valuesAreEmbedded() && !store.valuesAreSerialised() && delegate.size() != values.size())
        {
            // With Derby 10.x we can get instances where the values query returns no values yet entries is not empty
            // TODO Maybe make this throw an exception
            NucleusLogger.DATASTORE_RETRIEVE.warn("The number of Map value objects (" + values.size() + ")" + 
                " was different to the number of entries (" + delegate.size() + ")." +
                " Likely there is a bug in your datastore");
        }

        keys.clear();
        values.clear();
    }
    
    /**
     * Returns <tt>true</tt> if this collection contains the specified
     * element.  More formally, returns <tt>true</tt> if and only if this
     * collection contains at least one element <tt>it</tt> such that
     * <tt>(o==null ? it==null : o.equals(it))</tt>.<p>
     * <p>
     * This implementation iterates over the elements in the collection,
     * checking each element in turn for equality with the specified element.
     * @param backingStore the Store
     * @param op the ObjectProvider
     * @return <tt>true</tt> if this collection contains the specified element.
     */
     public static Object[] toArray(CollectionStore backingStore, ObjectProvider op)
     {
         Object[] result = new Object[backingStore.size(op)];
         Iterator it = backingStore.iterator(op);
         for (int i=0; it.hasNext(); i++)
         {
             result[i] = it.next();
         }        
         return result;
    }

    /**
     * Returns an array containing all of the elements in this collection; 
     * 
     * @param  backingStore the Store
     * @param  op the ObjectProvider
     * @param  a the array into which the elements of the collection are to
     *         be stored, if it is big enough; otherwise, a new array of the
     *         same runtime type is allocated for this purpose.
     * @return an array containing the elements of the collection.
     * 
     * @throws NullPointerException if the specified array is <tt>null</tt>.
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in this
     *         collection.
     */
    public static Object[] toArray(CollectionStore backingStore, ObjectProvider op, Object a[])
    {
        int size = backingStore.size(op);
        if (a.length < size)
        {
            a = (Object[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        }
        Iterator it=backingStore.iterator(op);
        for (int i=0; i<size; i++)
        {
            a[i] = it.next();
        }

        if (a.length > size)
        {
            a[size] = null;
        }

        return a;        
    }

    /**
     * Convenience method for creating a Comparator using extension metadata tags for the specified field.
     * Uses the extension key "comparator-name".
     * @param fmd The field that needs the comparator
     * @param clr ClassLoader resolver
     * @return The Comparator
     */
    public static Comparator getComparator(AbstractMemberMetaData fmd, ClassLoaderResolver clr)
    {
        Comparator comparator = null;
        String comparatorName = null;
        if (fmd.hasMap() && fmd.getMap().hasExtension("comparator-name"))
        {
            comparatorName = fmd.getMap().getValueForExtension("comparator-name");
        }
        else if (fmd.hasCollection() && fmd.getCollection().hasExtension("comparator-name"))
        {
            comparatorName = fmd.getCollection().getValueForExtension("comparator-name");
        }

        if (comparatorName != null)
        {
            Class comparatorCls = null;
            try
            {
                comparatorCls = clr.classForName(comparatorName);
                comparator = (Comparator)ClassUtils.newInstance(comparatorCls, null, null);
            }
            catch (NucleusException jpe)
            {
                NucleusLogger.PERSISTENCE.warn(LOCALISER.msg("023012", fmd.getFullFieldName(), comparatorName));
            }
        }
        return comparator;
    }

    /**
     * Convenience method to refresh fetch plan fields for all elements for a collection field.
     * All elements that are PersistenceCapable will be made transient.
     * @param ownerOP ObjectProvider for the owning object with the collection
     * @param elements The elements in the collection
     */
    public static void refreshFetchPlanFieldsForCollection(ObjectProvider ownerOP, Object[] elements)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (int i = 0; i < elements.length; i++)
        {
            if (api.isPersistable(elements[i]))
            {
                ownerOP.getExecutionContext().refreshObject(elements[i]);
            }
        }
    }

    /**
     * Convenience method to refresh fetch plan fields for all elements for a map field.
     * All elements that are PersistenceCapable will be made transient.
     * @param ownerOP ObjectProvider for the owning object with the map
     * @param entries The entries in the map
     */
    public static void refreshFetchPlanFieldsForMap(ObjectProvider ownerOP, Set entries)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (Iterator it = entries.iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry) it.next();
            Object val = entry.getValue();
            Object key = entry.getKey();
            if (api.isPersistable(key))
            {
               ownerOP.getExecutionContext().refreshObject(key);
            }
            if (api.isPersistable(val))
            {
               ownerOP.getExecutionContext().refreshObject(val);
            }
        }
    }

    /**
     * Convenience method to detach (recursively) all elements for a collection field.
     * All elements that are PersistenceCapable will be detached.
     * @param ownerOP ObjectProvider for the owning object with the collection
     * @param elements The elements in the collection
     * @param state FetchPlan state
     */
    public static void detachForCollection(ObjectProvider ownerOP, Object[] elements, FetchPlanState state)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (int i = 0; i < elements.length; i++)
        {
            if (api.isPersistable(elements[i]))
            {
                ownerOP.getExecutionContext().detachObject(elements[i], state);
            }
        }
    }

    /**
     * Convenience method to detach copies (recursively) of all elements for a collection field.
     * All elements that are PersistenceCapable will be detached.
     * @param ownerOP ObjectProvider for the owning object with the collection
     * @param elements The elements in the collection
     * @param state FetchPlan state
     * @param detached Collection to add the detached copies to
     */
    public static void detachCopyForCollection(ObjectProvider ownerOP, Object[] elements, FetchPlanState state, Collection detached)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (int i = 0; i < elements.length; i++)
        {
            if (elements[i] == null)
            {
                detached.add(null);
            }
            else
            {
                Object object = elements[i];
                if (api.isPersistable(object))
                {
                    detached.add(ownerOP.getExecutionContext().detachObjectCopy(object, state));
                }
                else
                {
                    detached.add(object);
                }
            }
        }
    }

    /**
     * Convenience method to attach (recursively) all elements for a collection field.
     * All elements that are PersistenceCapable and not yet having an attached object will be attached.
     * @param ownerOP ObjectProvider for the owning object with the collection
     * @param elements The elements to process
     * @param elementsWithoutIdentity Whether the elements have their own identity
     */
    public static void attachForCollection(ObjectProvider ownerOP, Object[] elements, boolean elementsWithoutIdentity)
    {
        ExecutionContext ec = ownerOP.getExecutionContext();
        ApiAdapter api = ec.getApiAdapter();
        for (int i = 0; i < elements.length; i++)
        {
            if (api.isPersistable(elements[i]))
            {
                Object attached = ec.getAttachedObjectForId(api.getIdForObject(elements[i]));
                if (attached == null)
                {
                    // Not yet attached so attach
                    ec.attachObject(ownerOP, elements[i], elementsWithoutIdentity);
                }
            }
        }
    }

    /**
     * Method to return an attached copy of the passed (detached) value. The returned attached copy
     * is a SCO wrapper. Goes through the existing elements in the store for this owner field and
     * removes ones no longer present, and adds new elements. All elements in the (detached)
     * value are attached.
     * @param ownerOP ObjectProvider for the owning object with the collection
     * @param detachedElements The detached elements in the collection
     * @param attached Collection to add the attached copies to
     * @param elementsWithoutIdentity Whether the elements have their own identity
     */
    public static void attachCopyForCollection(ObjectProvider ownerOP, Object[] detachedElements,
            Collection attached, boolean elementsWithoutIdentity)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (int i = 0; i < detachedElements.length; i++)
        {
            if (api.isPersistable(detachedElements[i]) && api.isDetachable(detachedElements[i]))
            {
                attached.add(ownerOP.getExecutionContext().attachObjectCopy(ownerOP, detachedElements[i], elementsWithoutIdentity));
            }
            else
            {
                attached.add(detachedElements[i]);
            }
        }
    }

    /**
     * Convenience method to detach (recursively) all elements for a map field.
     * All elements that are PersistenceCapable will be detached.
     * @param ownerOP ObjectProvider for the owning object with the map
     * @param entries The entries in the map
     * @param state FetchPlan state
     */
    public static void detachForMap(ObjectProvider ownerOP, Set entries, FetchPlanState state)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (Iterator it = entries.iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry) it.next();
            Object val = entry.getValue();
            Object key = entry.getKey();
            if (api.isPersistable(key))
            {
               ownerOP.getExecutionContext().detachObject(key, state);
            }
            if (api.isPersistable(val))
            {
               ownerOP.getExecutionContext().detachObject(val, state);
            }
        }
    }

    /**
     * Convenience method to detach copies (recursively) of all elements for a map field.
     * All elements that are PersistenceCapable will be detached.
     * @param ownerOP ObjectProvider for the owning object with the map
     * @param entries The entries in the map
     * @param state FetchPlan state
     * @param detached Map to add the detached copies to
     */
    public static void detachCopyForMap(ObjectProvider ownerOP, Set entries, FetchPlanState state, Map detached)
    {
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        for (Iterator it = entries.iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry) it.next();
            Object val = entry.getValue();
            Object key = entry.getKey();
            if (api.isPersistable(val))
            {
                val = ownerOP.getExecutionContext().detachObjectCopy(val, state);
            }
            if (api.isPersistable(key))
            {
                key = ownerOP.getExecutionContext().detachObjectCopy(key, state);
            }
            detached.put(key, val);
        }
    }

    /**
     * Convenience method to attach (recursively) all keys/values for a map field.
     * All keys/values that are PersistenceCapable and don't already have an attached object will be attached.
     * @param ownerOP ObjectProvider for the owning object with the map
     * @param entries The entries in the map to process
     * @param keysWithoutIdentity Whether the keys have their own identity
     * @param valuesWithoutIdentity Whether the values have their own identity
     */
    public static void attachForMap(ObjectProvider ownerOP, Set entries, boolean keysWithoutIdentity, boolean valuesWithoutIdentity)
    {
        ExecutionContext ec = ownerOP.getExecutionContext();
        ApiAdapter api = ec.getApiAdapter();
        for (Iterator it = entries.iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry) it.next();
            Object val = entry.getValue();
            Object key = entry.getKey();
            if (api.isPersistable(key))
            {
                Object attached = ec.getAttachedObjectForId(api.getIdForObject(key));
                if (attached == null)
                {
                    // Not yet attached so attach
                    ownerOP.getExecutionContext().attachObject(ownerOP, key, keysWithoutIdentity);
                }
            }
            if (api.isPersistable(val))
            {
                Object attached = ec.getAttachedObjectForId(api.getIdForObject(val));
                if (attached == null)
                {
                    // Not yet attached so attach
                    ownerOP.getExecutionContext().attachObject(ownerOP, val, valuesWithoutIdentity);
                }
            }
        }
    }

    /**
     * Method to return an attached copy of the passed (detached) value. The returned attached copy
     * is a SCO wrapper. Goes through the existing elements in the store for this owner field and
     * removes ones no longer present, and adds new elements. All elements in the (detached)
     * value are attached.
     * @param ownerOP ObjectProvider for the owning object with the map
     * @param detachedEntries The detached entries in the map
     * @param attached Map to add the attached copies to
     * @param keysWithoutIdentity Whether the keys have their own identity
     * @param valuesWithoutIdentity Whether the values have their own identity
     */
    public static void attachCopyForMap(ObjectProvider ownerOP, Set detachedEntries,
            Map attached, boolean keysWithoutIdentity, boolean valuesWithoutIdentity)
    {
        Iterator iter = detachedEntries.iterator();
        ApiAdapter api = ownerOP.getExecutionContext().getApiAdapter();
        while (iter.hasNext())
        {
            Map.Entry entry = (Map.Entry) iter.next();
            Object val = entry.getValue();
            Object key = entry.getKey();
            if (api.isPersistable(val) && api.isDetachable(val))
            {
                val = ownerOP.getExecutionContext().attachObjectCopy(ownerOP, val, valuesWithoutIdentity);
            }
            if (api.isPersistable(key) && api.isDetachable(key))
            {
                key = ownerOP.getExecutionContext().attachObjectCopy(ownerOP, key, keysWithoutIdentity);
            }
            attached.put(key, val);
        }
    }

    /**
     * Method to check if an object to be stored in a SCO container is already persistent, or is managed
     * by a different ObjectManager. If not persistent, this call will persist it. If not yet flushed to the
     * datastore this call will flush it.
     * @param ec ExecutionContext
     * @param object The object
     * @param fieldValues Values for any fields when persisting (if the object needs persisting)
     * @return Whether the object was persisted during this call
     */
    public static boolean validateObjectForWriting(ExecutionContext ec, Object object, FieldValues fieldValues)
    {
        boolean persisted = false;
        ApiAdapter api = ec.getApiAdapter();
        if (api.isPersistable(object))
        {
            ExecutionContext objectEC = api.getExecutionContext(object);
            if (objectEC != null && ec != objectEC)
            {
                throw new NucleusUserException(LOCALISER.msg("023009", StringUtils.toJVMIDString(object)), 
                    api.getIdForObject(object));
            }
            else if (!api.isPersistent(object))
            {
                // Not persistent, so either is detached, or needs persisting for first time
                boolean exists = false;
                if (api.isDetached(object))
                {
                    if (ec.getNucleusContext().getPersistenceConfiguration().getBooleanProperty("datanucleus.attachSameDatastore"))
                    {
                        // Assume that it is detached from this datastore
                        exists = true;
                    }
                    else
                    {
                        // Check if the (attached) object exists in this datastore
                        try
                        {
                            Object obj = ec.findObject(api.getIdForObject(object), true, false, object.getClass().getName());
                            if (obj != null)
                            {
                                // PM.getObjectById creates a dummy object to represent this object and automatically
                                // enlists it in the txn. Evict it to avoid issues with reachability
                                ObjectProvider objSM = ec.findObjectProvider(obj);
                                if (objSM != null)
                                {
                                    ec.evictFromTransaction(objSM);
                                }
                            }
                            exists = true;
                        }
                        catch (NucleusObjectNotFoundException onfe)
                        {
                            exists = false;
                        }
                    }
                }
                if (!exists)
                {
                    // Persist the object
                    ec.persistObjectInternal(object, fieldValues, ObjectProvider.PC);
                    persisted = true;
                }
            }
            else
            {
                // Persistent, but is it flushed to the datastore?
                ObjectProvider objectSM = ec.findObjectProvider(object);
                if (objectSM.isWaitingToBeFlushedToDatastore())
                {
                    // Newly persistent but still not flushed (e.g in optimistic txn)
                    // Process any fieldValues
                    if (fieldValues != null)
                    {
                        objectSM.loadFieldValues(fieldValues);
                    }

                    // Now flush it
                    objectSM.flush();
                }
            }
        }
        return persisted;
    }

    /**
     * Method to check if objects to be stored in a SCO container are already persistent, or are managed by 
     * a different ObjectManager. If not persistent, this call will persist them.
     * @param ec ExecutionContext
     * @param objects The objects (array, or Collection)
     */
    public static void validateObjectsForWriting(ExecutionContext ec, Object objects)
    {
        if (objects != null)
        {
            if (objects.getClass().isArray())
            {
                if (!objects.getClass().getComponentType().isPrimitive())
                {
                    Object[] obj = ((Object[]) objects);
                    for (int i = 0; i < obj.length; i++)
                    {
                        validateObjectForWriting(ec, obj[i], null);
                    }
                }
            }
            else if (objects instanceof Collection)
            {
                Collection col = (Collection) objects;
                Iterator it = col.iterator();
                while (it.hasNext())
                {
                    validateObjectForWriting(ec, it.next(), null);
                }
            }
        }
    }

    /**
     * Return whether the supplied type (collection) is list based.
     * @return Whether it needs list ordering
     */
    public static boolean isListBased(Class type)
    {
        if (type == null)
        {
            return false;
        }
        else if (java.util.List.class.isAssignableFrom(type))
        {
            return true;
        }
        else if (java.util.Queue.class.isAssignableFrom(type))
        {
            // Queue needs ordering
            return true;
        }
        return false;
    }

    /**
     * Method to return the type to instantiate a container as.
     * Returns the declared type unless it is not a concrete type, in which case returns ArrayList, HashSet,
     * or HashMap.
     * @param declaredType The declared type
     * @param ordered Hint whether it needs ordering or not (null implies not)
     * @return The type to instantiate as
     */
    public static Class getContainerInstanceType(Class declaredType, Boolean ordered)
    {
        if (declaredType.isInterface())
        {
            // Instantiate as ArrayList/HashSet/HashMap
            if (List.class.isAssignableFrom(declaredType))
            {
                return ArrayList.class;
            }
            else if (Set.class.isAssignableFrom(declaredType))
            {
                return HashSet.class;
            }
            else if (Map.class.isAssignableFrom(declaredType))
            {
                return HashMap.class;
            }
            else if (ordered)
            {
                return ArrayList.class;
            }
            else
            {
                return HashSet.class;
            }
        }
        return declaredType;
    }

    /**
     * Convenience accessor for whether to detach SCO objects as wrapped.
     * @param ownerOP ObjectProvider
     * @return Whether to detach SCOs in wrapped form
     */
    public static boolean detachAsWrapped(ObjectProvider ownerOP)
    {
        return ownerOP.getExecutionContext().getNucleusContext().getPersistenceConfiguration().getBooleanProperty("datanucleus.detachAsWrapped");
    }

    /**
     * Convenience method to return if we should use a queued update for the current operation.
     * If within a transaction, and using queueing in general, and not flushing then returns true.
     * @param queued Whether supporting queued operations
     * @param op ObjectProvider
     * @return Whether to use queued for this operation
     */
    public static boolean useQueuedUpdate(boolean queued, ObjectProvider op)
    {
        return queued && !op.getExecutionContext().isFlushing() && op.getExecutionContext().getTransaction().isActive();
    }

    /**
     * Method to return if the member is a collection/array with dependent element.
     * @param mmd member metadata
     * @return whether it has dependent element
     */
    public static boolean hasDependentElement(AbstractMemberMetaData mmd)
    {
        if (!SCOUtils.collectionHasElementsWithoutIdentity(mmd) && mmd.getCollection() != null && 
            mmd.getCollection().isDependentElement())
        {
            return true;
        }
        return false;
    }

    /**
     * Method to return if the member is a map with dependent key.
     * @param mmd member metadata
     * @return whether it has dependent key
     */
    public static boolean hasDependentKey(AbstractMemberMetaData mmd)
    {
        if (!SCOUtils.mapHasKeysWithoutIdentity(mmd) && mmd.getMap() != null && 
            mmd.getMap().isDependentKey())
        {
            return true;
        }
        return false;
    }

    /**
     * Method to return if the member is a map with dependent value.
     * @param mmd member metadata
     * @return whether it has dependent value
     */
    public static boolean hasDependentValue(AbstractMemberMetaData mmd)
    {
        if (!SCOUtils.mapHasValuesWithoutIdentity(mmd) && mmd.getMap() != null && 
            mmd.getMap().isDependentValue())
        {
            return true;
        }
        return false;
    }
}