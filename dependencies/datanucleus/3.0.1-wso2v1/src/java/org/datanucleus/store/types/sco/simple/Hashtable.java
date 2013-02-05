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
package org.datanucleus.store.types.sco.simple;

import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Iterator;

import javax.jdo.spi.PersistenceCapable;

import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.state.FetchPlanState;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.types.sco.SCOMap;
import org.datanucleus.store.types.sco.SCOUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

/**
 * A mutable second-class Hashtable object.
 * This is the simplified form that intercepts mutators and marks the field as dirty.
 * It also handles cascade-delete triggering for persistable elements.
 */
public class Hashtable extends java.util.Hashtable implements SCOMap, Cloneable
{
    protected static final Localiser LOCALISER = Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    protected transient Object owner;
    protected transient ObjectProvider ownerSM;
    protected transient String fieldName;
    protected transient int fieldNumber;

    /** The internal "delegate". */
    protected java.util.Hashtable delegate;

    /**
     * Constructor
     * @param ownerSM the owner of this Map
     * @param fieldName the declared field name
     */
    public Hashtable(ObjectProvider ownerSM, String fieldName)
    {
        this.ownerSM = ownerSM;
        this.owner = ownerSM.getObject();
        this.fieldName = fieldName;
        this.fieldNumber = ownerSM.getClassMetaData().getMetaDataForMember(fieldName).getAbsoluteFieldNumber();
    }

    /**
     * Method to initialise the SCO from an existing value.
     * @param o Object to set value using.
     * @param forInsert Whether the object needs inserting in the datastore with this value
     * @param forUpdate Whether to update the datastore with this value
     */
    public void initialise(Object o, boolean forInsert, boolean forUpdate)
    {
        java.util.Map m = (java.util.Map)o;
        if (m != null)
        {
            delegate = new java.util.Hashtable(m); // Make copy of container rather than using same memory
        }
        else
        {
            delegate = new java.util.Hashtable();
        }
        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("023003", 
                ownerSM.toPrintableID(), fieldName, "" + size(), 
                SCOUtils.getSCOWrapperOptionsMessage(true, false, false, false)));
        }
    }

    /**
     * Method to initialise the SCO for use.
     */
    public void initialise()
    {
        delegate = new java.util.Hashtable();
        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("023003", 
                ownerSM.toPrintableID(), fieldName, "" + size(), 
                SCOUtils.getSCOWrapperOptionsMessage(true, false, false, false)));
        }
    }

    /**
     * Accessor for the unwrapped value that we are wrapping.
     * @return The unwrapped value
     */
    public Object getValue()
    {
        return delegate;
    }

    /**
     * Method to effect the load of the data in the SCO.
     * Used when the SCO supports lazy-loading to tell it to load all now.
     */
    public void load()
    {
        // Always loaded
    }

    /**
     * Method to return if the SCO has its contents loaded. Returns true.
     * @return Whether it is loaded
     */
    public boolean isLoaded()
    {
        return true;
    }

    /**
     * Method to flush the changes to the datastore when operating in queued mode.
     * Does nothing in "direct" mode.
     */
    public void flush()
    {
        // Never queued
    }

    /**
     * Method to update an embedded key in this map.
     * @param key The key
     * @param fieldNumber Number of field in the key
     * @param newValue New value for this field
     */
    public void updateEmbeddedKey(Object key, int fieldNumber, Object newValue)
    {
        // Just mark field in embedded owners as dirty
        makeDirty();
    }

    /**
     * Method to update an embedded value in this map.
     * @param value The value
     * @param fieldNumber Number of field in the value
     * @param newValue New value for this field
     */
    public void updateEmbeddedValue(Object value, int fieldNumber, Object newValue)
    {
        // Just mark field in embedded owners as dirty
        makeDirty();
    }

    /**
     * Accessor for the field name that this Hashtable relates to.
     * @return The field name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    /**
     * Accessor for the owner that this Hashtable relates to.
     * @return The owner
     */
    public Object getOwner()
    {
        return (ownerSM != null ? ownerSM.getObject() : null);
    }

    /**
     * Method to unset the owner and field details.
     */
    public synchronized void unsetOwner()
    {
        if (ownerSM != null)
        {
            ownerSM = null;
        }
    }

    /**
     * Utility to mark the object as dirty
     */
    public void makeDirty()
    {
        if (owner != null)
        {
            ((PersistenceCapable)owner).jdoMakeDirty(fieldName);
        }
    }

    /**
     * Method to return a detached copy of the container.
     * Recurse sthrough the keys/values so that they are likewise detached.
     * @param state State for detachment process
     * @return The detached container
     */
    public Object detachCopy(FetchPlanState state)
    {
        java.util.Map detached = new java.util.Hashtable();
        SCOUtils.detachCopyForMap(ownerSM, entrySet(), state, detached);
        return detached;
    }

    /**
     * Method to return an attached copy of the passed (detached) value. The returned attached copy
     * is a SCO wrapper. Goes through the existing keys/values in the store for this owner field and
     * removes ones no longer present, and adds new keys/values. All keys/values in the (detached)
     * value are attached.
     * @param value The new (map) value
     */
    public void attachCopy(Object value)
    {
        java.util.Map m = (java.util.Map) value;

        // Attach all of the keys/values in the new map
        AbstractMemberMetaData fmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
        boolean keysWithoutIdentity = SCOUtils.mapHasKeysWithoutIdentity(fmd);
        boolean valuesWithoutIdentity = SCOUtils.mapHasValuesWithoutIdentity(fmd);

        java.util.Map attachedKeysValues = new java.util.HashMap(m.size());
        SCOUtils.attachCopyForMap(ownerSM, m.entrySet(), attachedKeysValues, keysWithoutIdentity, valuesWithoutIdentity);

        // Update the attached map with the detached elements
        SCOUtils.updateMapWithMapKeysValues(ownerSM.getExecutionContext().getApiAdapter(), this, attachedKeysValues);
    }

    // ------------------ Implementation of Hashtable methods ------------------
 
    /**
     * Creates and returns a copy of this object.
     *
     * <P>Mutable second-class Objects are required to provide a public
     * clone method in order to allow for copying PersistenceCapable
     * objects. In contrast to Object.clone(), this method must not throw a
     * CloneNotSupportedException.
     * @return The cloned object
     */
    public Object clone()
    {
        return delegate.clone();
    }

    /**
     * Method to return if the map contains this key
     * @param key The key
     * @return Whether it is contained
     **/
    public boolean containsKey(Object key)
    {
        return delegate.containsKey(key);
    }

    /**
     * Method to return if the map contains this value.
     * @param value The value
     * @return Whether it is contained
     **/
    public boolean containsValue(Object value)
    {
        return delegate.containsValue(value);
    }

    /**
     * Accessor for the set of entries in the Map.
     * @return Set of entries
     **/
    public java.util.Set entrySet()
    {
        return delegate.entrySet();
    }

    /**
     * Method to check the equality of this map, and another.
     * @param o The map to compare against.
     * @return Whether they are equal.
     **/
    public synchronized boolean equals(Object o)
    {
        return delegate.equals(o);
    }

    /**
     * Accessor for the value stored against a key.
     * @param key The key
     * @return The value.
     **/
    public Object get(Object key)
    {
        return delegate.get(key);
    }

    /**
     * Method to generate a hashcode for this Map.
     * @return The hashcode.
     **/
    public synchronized int hashCode()
    {
        return delegate.hashCode();
    }

    /**
     * Method to return if the Map is empty.
     * @return Whether it is empty.
     **/
    public boolean isEmpty()
    {
        return delegate.isEmpty();
    }

    /**
     * Accessor for the set of keys in the Map.
     * @return Set of keys.
     **/
    public java.util.Set keySet()
    {
        return delegate.keySet();
    }

    /**
     * Method to return the size of the Map.
     * @return The size
     **/
    public int size()
    {
        return delegate.size();
    }

    /**
     * Accessor for the set of values in the Map.
     * @return Set of values.
     **/
    public Collection values()
    {
        return delegate.values();
    }

    // -------------------------------- Mutator methods ------------------------
 
    /**
     * Method to clear the Hashtable
     */
    public synchronized void clear()
    {
        if (ownerSM != null && !delegate.isEmpty())
        {
            // Cascade delete
            AbstractMemberMetaData mmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
            if (SCOUtils.hasDependentKey(mmd) || SCOUtils.hasDependentValue(mmd)) 
            {
                Iterator<Map.Entry> entryIter = delegate.entrySet().iterator();
                while (entryIter.hasNext())
                {
                    Map.Entry entry = entryIter.next();
                    if (SCOUtils.hasDependentKey(mmd))
                    {
                        ownerSM.getExecutionContext().deleteObjectInternal(entry.getKey());
                    }
                    if (SCOUtils.hasDependentValue(mmd))
                    {
                        ownerSM.getExecutionContext().deleteObjectInternal(entry.getValue());
                    }
                }
            }
        }

        delegate.clear();

        makeDirty();
    }

    /**
     * Method to add a value against a key to the Hashtable
     * @param key The key
     * @param value The value
     * @return The previous value for the specified key.
     */
    public Object put(Object key,Object value)
    {
        Object oldValue = delegate.put(key, value);
        makeDirty();
        return oldValue;
    }

    /**
     * Method to add the specified Map's values under their keys here.
     * @param m The map
     */
    public void putAll(java.util.Map m)
    {
        delegate.putAll(m);
        makeDirty();
    }

    /**
     * Method to remove the value for a key from the Hashtable
     * @param key The key to remove
     * @return The value that was removed from this key.
     */
    public Object remove(Object key)
    {
        Object value = delegate.remove(key);

        if (ownerSM != null)
        {
            // Cascade delete
            AbstractMemberMetaData mmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
            if (SCOUtils.hasDependentKey(mmd) || SCOUtils.hasDependentValue(mmd)) 
            {
                if (SCOUtils.hasDependentKey(mmd))
                {
                    ownerSM.getExecutionContext().deleteObjectInternal(key);
                }
                if (SCOUtils.hasDependentValue(mmd))
                {
                    ownerSM.getExecutionContext().deleteObjectInternal(value);
                }
            }
        }

        makeDirty();

        return value;
    }

    /**
     * The writeReplace method is called when ObjectOutputStream is preparing
     * to write the object to the stream. The ObjectOutputStream checks whether
     * the class defines the writeReplace method. If the method is defined, the
     * writeReplace method is called to allow the object to designate its
     * replacement in the stream. The object returned should be either of the
     * same type as the object passed in or an object that when read and 
     * resolved will result in an object of a type that is compatible with all
     * references to the object.
     * 
     * @return the replaced object
     * @throws ObjectStreamException
     */
    protected Object writeReplace() throws ObjectStreamException
    {
        return new java.util.Hashtable(delegate);
    }
}