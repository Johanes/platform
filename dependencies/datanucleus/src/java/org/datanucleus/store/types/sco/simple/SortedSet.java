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
import java.util.Comparator;
import java.util.Iterator;

import javax.jdo.spi.PersistenceCapable;

import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.state.FetchPlanState;
import org.datanucleus.state.RelationshipManager;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.types.sco.SCOCollection;
import org.datanucleus.store.types.sco.SCOCollectionIterator;
import org.datanucleus.store.types.sco.SCOMtoN;
import org.datanucleus.store.types.sco.SCOUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

/**
 * A mutable second-class SortedSet object.
 * This is the simplified form that intercepts mutators and marks the field as dirty.
 * It also handles cascade-delete triggering for persistable elements.
 */
public class SortedSet extends java.util.AbstractSet implements java.util.SortedSet, SCOCollection, SCOMtoN, Cloneable, 
    java.io.Serializable
{
    protected static final Localiser LOCALISER = Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    protected transient Object owner;
    protected transient ObjectProvider ownerSM;
    protected transient String fieldName;
    protected transient int fieldNumber;

    /** The internal "delegate". */
    protected java.util.TreeSet delegate;

    /**
     * Constructor, using the StateManager of the "owner" and the field name.
     * @param ownerSM The owner StateManager
     * @param fieldName The name of the field of the SCO.
     */
    public SortedSet(ObjectProvider ownerSM, String fieldName)
    {
        this.ownerSM = ownerSM;
        this.owner = ownerSM.getObject();
        this.fieldName = fieldName;
        this.fieldNumber = ownerSM.getClassMetaData().getMetaDataForMember(fieldName).getAbsoluteFieldNumber();
    }

    /**
     * Method to initialise the SCO from an existing value.
     * @param o  The object to set from
     * @param forInsert Whether the object needs inserting in the datastore with this value
     * @param forUpdate Whether to update the datastore with this value
     */
    public void initialise(Object o, boolean forInsert, boolean forUpdate)
    {
        Collection c = (Collection)o;
        if (c != null)
        {
            initialiseDelegate();
            delegate.addAll(c);
        }
        else
        {
            initialiseDelegate();
        }
        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("023003", 
                ownerSM.toPrintableID(), fieldName, "" + size(), 
                SCOUtils.getSCOWrapperOptionsMessage(true, false, true, false)));
        }
    }

    /**
     * Method to initialise the SCO for use.
     */
    public void initialise()
    {
        initialiseDelegate();
        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("023003", 
                ownerSM.toPrintableID(), fieldName, "" + size(), 
                SCOUtils.getSCOWrapperOptionsMessage(true, false, true, false)));
        }
    }

    /**
     * Convenience method to set up the delegate respecting any comparator specified in MetaData.
     */
    protected void initialiseDelegate()
    {
        AbstractMemberMetaData fmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
        Comparator comparator = SCOUtils.getComparator(fmd, ownerSM.getExecutionContext().getClassLoaderResolver());
        if (comparator != null)
        {
            this.delegate = new java.util.TreeSet(comparator);
        }
        else
        {
            this.delegate = new java.util.TreeSet();
        }
    }

    // ----------------------- Implementation of SCO methods -------------------

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
     * Method to update an embedded element in this collection.
     * @param element The element
     * @param fieldNumber Number of field in the element
     * @param value New value for this field
     */
    public void updateEmbeddedElement(Object element, int fieldNumber, Object value)
    {
        // Just mark field in embedded owners as dirty
        makeDirty();
    }

    /**
     * Accessor for the field name.
     * @return The field name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    /**
     * Accessor for the owner object.
     * @return The owner object
     */
    public Object getOwner()
    {
        return (ownerSM != null ? ownerSM.getObject() : null);
    }

    /**
     * Method to unset the owner and field information.
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
     **/
    public void makeDirty()
    {
        if (owner != null)
        {
            ((PersistenceCapable)owner).jdoMakeDirty(fieldName);
        }
    }

    /**
     * Method to return a detached copy of the container.
     * Recurses through the elements so that they are likewise detached.
     * @param state State for detachment process
     * @return The detached container
     */
    public Object detachCopy(FetchPlanState state)
    {
        AbstractMemberMetaData fmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
        Comparator comparator = SCOUtils.getComparator(fmd, ownerSM.getExecutionContext().getClassLoaderResolver());
        java.util.Collection detached = null;
        if (comparator != null)
        {
            detached = new java.util.TreeSet(comparator);
        } 
        else
        {
            detached = new java.util.TreeSet();
        }
        SCOUtils.detachCopyForCollection(ownerSM, toArray(), state, detached);
        return detached;
    }

    /**
     * Method to return an attached copy of the passed (detached) value. The returned attached copy
     * is a SCO wrapper. Goes through the existing elements in the store for this owner field and
     * removes ones no longer present, and adds new elements. All elements in the (detached)
     * value are attached.
     * @param value The new (collection) value
     */
    public void attachCopy(Object value)
    {
        java.util.Collection c = (java.util.Collection) value;
        AbstractMemberMetaData fmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
        boolean elementsWithoutIdentity = SCOUtils.collectionHasElementsWithoutIdentity(fmd);
        java.util.Collection attachedElements = new java.util.HashSet(c.size());

        // Remove any no-longer-needed elements from this collection
        SCOUtils.attachRemoveDeletedElements(ownerSM.getExecutionContext().getApiAdapter(), this, c, elementsWithoutIdentity);

        // Persist any new elements and form the attached elements collection
        SCOUtils.attachCopyForCollection(ownerSM, c.toArray(), attachedElements, elementsWithoutIdentity);

        // Add any new elements to this collection
        SCOUtils.attachAddNewElements(ownerSM.getExecutionContext().getApiAdapter(), this, attachedElements,
            elementsWithoutIdentity);
    }

    // ------------------ Implementation of SortedSet methods --------------------

    /**
     * Creates and returns a copy of this object.
     * @return The cloned object
     */
    public Object clone()
    {
        return delegate.clone();
    }

    /**
     * Accessor for the comparator.
     * @return The comparator
     */
    public Comparator comparator()
    {
        return delegate.comparator();
    }

    /**
     * Accessor for whether an element is contained in this Set.
     * @param element The element
     * @return Whether it is contained.
     **/
    public boolean contains(Object element)
    {
        return delegate.contains(element);
    }

    /**
     * Accessor for whether a collection is contained in this Set.
     * @param c The collection
     * @return Whether it is contained.
     **/
    public synchronized boolean containsAll(java.util.Collection c)
    {
        return delegate.containsAll(c);
    }

    /**
     * Equality operator.
     * @param o The object to compare against.
     * @return Whether this object is the same.
     **/
    public synchronized boolean equals(Object o)
    {
        return delegate.equals(o);
    }

    /**
     * Accessor for the first element in the sorted set.
     * @return The first element
     **/
    public Object first()
    {
        return delegate.first();
    }

    /**
     * Hashcode operator.
     * @return The Hash code.
     **/
    public synchronized int hashCode()
    {
        return delegate.hashCode();
    }

    /**
     * Accessor for whether the SortedSet is empty.
     * @return Whether it is empty.
     **/
    public boolean isEmpty()
    {
        return delegate.isEmpty();
    }

    /**
     * Accessor for an iterator for the Set.
     * @return The iterator
     **/
    public Iterator iterator()
    {
        return new SCOCollectionIterator(this, ownerSM, delegate, null, true);
    }

    /**
     * Method to retrieve the head elements up to the specified element.
     * @param toElement the element to return up to.
     * @return The set of elements meeting the input
     */
    public java.util.SortedSet headSet(Object toElement)
    {
        return delegate.headSet(toElement);
    }

    /**
     * Method to retrieve the subset of elements between the specified elements.
     * @param fromElement The start element
     * @param toElement The end element
     * @return The set of elements meeting the input
     */
    public java.util.SortedSet subSet(Object fromElement, Object toElement)
    {
        return delegate.subSet(fromElement, toElement);
    }

    /**
     * Method to retrieve the set of elements after the specified element.
     * @param fromElement The start element
     * @return The set of elements meeting the input
     */
    public java.util.SortedSet tailSet(Object fromElement)
    {
        return delegate.headSet(fromElement);
    }

    /**
     * Accessor for the last element in the sorted set.
     * @return The last element
     **/
    public Object last()
    {
        return delegate.last();
    }

    /**
     * Accessor for the size of the SortedSet.
     * @return The size.
     **/
    public int size()
    {
        return delegate.size();
    }

    /**
     * Method to return the list as an array.
     * @return The array
     */
    public Object[] toArray()
    {
        return delegate.toArray();
    }

    /**
     * Method to return the list as an array.
     * @param a The runtime types of the array being defined by this param
     * @return The array
     */
    public Object[] toArray(Object a[])
    {
        return delegate.toArray(a);
    }

    /**
     * Method to add an element to the SortedSet.
     * @param element The new element
     * @return Whether it was added ok.
     */
    public boolean add(Object element)
    {
        boolean success = delegate.add(element);
        if (ownerSM != null && ownerSM.getExecutionContext().getManageRelations())
        {
            // Relationship management
            ownerSM.getExecutionContext().getRelationshipManager(ownerSM).relationAdd(fieldNumber, element);
        }
        if (success)
        {
            makeDirty();
        }
        return success;
    }

    /**
     * Method to add a collection to the SortedSet.
     * @param elements The collection
     * @return Whether it was added ok.
     **/
    public boolean addAll(Collection elements)
    {
        boolean success = delegate.addAll(elements);
        if (ownerSM != null && ownerSM.getExecutionContext().getManageRelations())
        {
            // Relationship management
            Iterator iter = elements.iterator();
            while (iter.hasNext())
            {
                ownerSM.getExecutionContext().getRelationshipManager(ownerSM).relationAdd(fieldNumber, iter.next());
            }
        }
        if (success)
        {
            makeDirty();
        }
        return success;
    }

    /**
     * Method to clear the SortedSet
     **/
    public void clear()
    {
        if (ownerSM != null && ownerSM.getExecutionContext().getManageRelations())
        {
            // Relationship management
            Iterator iter = delegate.iterator();
            RelationshipManager relMgr = ownerSM.getExecutionContext().getRelationshipManager(ownerSM);
            while (iter.hasNext())
            {
                relMgr.relationRemove(fieldNumber, iter.next());
            }
        }

        if (ownerSM != null && !delegate.isEmpty())
        {
            // Cascade delete
            AbstractMemberMetaData mmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
            if (SCOUtils.hasDependentElement(mmd))
            {
                Iterator iter = delegate.iterator();
                while (iter.hasNext())
                {
                    ownerSM.getExecutionContext().deleteObjectInternal(iter.next());
                }
            }
        }

        delegate.clear();

        makeDirty();
    }

    /**
     * Method to remove an element from the List
     * @param element The Element to remove
     * @return Whether it was removed successfully.
     **/
    public synchronized boolean remove(Object element)
    {
        return remove(element, true);
    }

    /**
     * Method to remove an element from the List
     * @param element The Element to remove
     * @return Whether it was removed successfully.
     **/
    public synchronized boolean remove(Object element, boolean allowCascadeDelete)
    {
        boolean success = delegate.remove(element);
        if (ownerSM != null && ownerSM.getExecutionContext().getManageRelations())
        {
            // Relationship management
            ownerSM.getExecutionContext().getRelationshipManager(ownerSM).relationRemove(fieldNumber, element);
        }

        if (ownerSM != null && allowCascadeDelete)
        {
            // Cascade delete
            AbstractMemberMetaData mmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
            if (SCOUtils.hasDependentElement(mmd))
            {
                ownerSM.getExecutionContext().deleteObjectInternal(element);
            }
        }

        if (success)
        {
            makeDirty();
        }

        return success;
    }

    /**
     * Method to remove all elements from the collection from the SortedSet.
     * @param elements The collection of elements to remove 
     * @return Whether it was removed ok.
     **/
    public boolean removeAll(java.util.Collection elements)
    {
        boolean success = delegate.removeAll(elements);
        if (ownerSM != null && ownerSM.getExecutionContext().getManageRelations())
        {
            // Relationship management
            Iterator iter = elements.iterator();
            RelationshipManager relMgr = ownerSM.getExecutionContext().getRelationshipManager(ownerSM);
            while (iter.hasNext())
            {
                relMgr.relationRemove(fieldNumber, iter.next());
            }
        }

        if (ownerSM != null && elements != null && !elements.isEmpty())
        {
            // Cascade delete
            AbstractMemberMetaData mmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
            if (SCOUtils.hasDependentElement(mmd))
            {
                Iterator iter = elements.iterator();
                while (iter.hasNext())
                {
                    ownerSM.getExecutionContext().deleteObjectInternal(iter.next());
                }
            }
        }

        if (success)
        {
            makeDirty();
        }

        return success;
    }

    /**
     * Method to retain a Collection of elements (and remove all others).
     * @param c The collection to retain
     * @return Whether they were retained successfully.
     **/
    public synchronized boolean retainAll(java.util.Collection c)
    {
        boolean success = delegate.retainAll(c);
        if (success)
        {
            makeDirty();
        }
        return success;
    }

    /**
     * The writeReplace method is called when ObjectOutputStream is preparing
     * to write the object to the stream. The ObjectOutputStream checks
     * whether the class defines the writeReplace method. If the method is
     * defined, the writeReplace method is called to allow the object to
     * designate its replacement in the stream. The object returned should be
     * either of the same type as the object passed in or an object that when
     * read and resolved will result in an object of a type that is compatible
     * with all references to the object.
     * @return the replaced object
     * @throws ObjectStreamException
     */
    protected Object writeReplace() throws ObjectStreamException
    {
        return new java.util.TreeSet(delegate);
    }
}