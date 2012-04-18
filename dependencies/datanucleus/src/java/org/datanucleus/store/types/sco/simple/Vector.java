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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ListIterator;

import javax.jdo.spi.PersistenceCapable;

import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.state.FetchPlanState;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.types.sco.SCOList;
import org.datanucleus.store.types.sco.SCOListIterator;
import org.datanucleus.store.types.sco.SCOUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

/**
 * A mutable second-class Vector object.
 * This is the simplified form that intercepts mutators and marks the field as dirty.
 * It also handles cascade-delete triggering for persistable elements.
 */
public class Vector extends java.util.Vector implements SCOList, Cloneable
{
    protected static final Localiser LOCALISER = Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    protected transient Object owner;
    protected transient ObjectProvider ownerSM;
    protected transient String fieldName;
    protected transient int fieldNumber;

    /** The internal "delegate". */
    protected java.util.Vector delegate;

    /**
     * Constructor, using the StateManager of the "owner" and the field name.
     * @param ownerSM The owner StateManager
     * @param fieldName The name of the field of the SCO.
     **/
    public Vector(ObjectProvider ownerSM, String fieldName)
    {
        this.ownerSM = ownerSM;
        this.owner = ownerSM.getObject();
        this.fieldName = fieldName;
        this.fieldNumber = ownerSM.getClassMetaData().getMetaDataForMember(fieldName).getAbsoluteFieldNumber();
    }

    /**
     * Method to initialise the SCO from an existing value.
     * @param o The object to set from
     * @param forInsert Whether the object needs inserting in the datastore with this value
     * @param forUpdate Whether to update the datastore with this value
     */
    public void initialise(Object o, boolean forInsert, boolean forUpdate)
    {
        Collection c = (Collection)o;
        if (c != null)
        {
            delegate = new java.util.Vector(c); // Make copy of container rather than using same memory
        }
        else
        {
            delegate = new java.util.Vector();
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
        delegate = new java.util.Vector();
        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("023003", 
                ownerSM.toPrintableID(), fieldName, "" + size(), 
                SCOUtils.getSCOWrapperOptionsMessage(true, false, true, false)));
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
     **/
    public String getFieldName()
    {
        return fieldName;
    }

    /**
     * Accessor for the owner object.
     * @return The owner object
     **/
    public Object getOwner()
    {
        return (ownerSM != null ? ownerSM.getObject() : null);
    }

    /**
     * Method to unset the owner and field information.
     **/
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
     * Recurse sthrough the elements so that they are likewise detached.
     * @param state State for detachment process
     * @return The detached container
     */
    public Object detachCopy(FetchPlanState state)
    {
        java.util.Collection detached = new java.util.Vector();
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

        // Attach all of the elements in the new list
        AbstractMemberMetaData fmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
        boolean elementsWithoutIdentity = SCOUtils.collectionHasElementsWithoutIdentity(fmd);

        java.util.List attachedElements = new java.util.ArrayList(c.size());
        SCOUtils.attachCopyForCollection(ownerSM, c.toArray(), attachedElements, elementsWithoutIdentity);

        // Update the attached list with the detached elements
        SCOUtils.updateListWithListElements(this, attachedElements);
    }

    // ------------------ Implementation of Vector methods ---------------------
 
    /**
     * Clone operator to return a copy of this object.
     * <p>
     * Mutable second-class Objects are required to provide a public
     * clone method in order to allow for copying PersistenceCapable
     * objects. In contrast to Object.clone(), this method must not throw a
     * CloneNotSupportedException.
     * </p>
     * @return The cloned object
     */
    public Object clone()
    {
        return delegate.clone();
    }

    /**
     * Method to return if the list contains this element.
     * @param element The element
     * @return Whether it is contained
     **/
    public boolean contains(Object element)
    {
        return delegate.contains(element);
    }

    /**
     * Accessor for whether a collection of elements are contained here.
     * @param c The collection of elements.
     * @return Whether they are contained.
     **/
    public synchronized boolean containsAll(java.util.Collection c)
    {
        return delegate.containsAll(c);
    }

    /**
     * Method to retrieve an element no.
     * @param index The item to retrieve
     * @return The element at that position.
     **/
    public Object elementAt(int index)
    {
        return delegate.elementAt(index);
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
     * Method to return the elements of the List as an Enumeration.
     * @return The elements
     */
    public Enumeration elements()
    {
        return delegate.elements();
    }

    /**
     * Method to return the first element in the Vector.
     * @return The first element
     */
    public Object firstElement()
    {
        return delegate.firstElement();
    }

    /**
     * Method to retrieve an element no.
     * @param index The item to retrieve
     * @return The element at that position.
     **/
    public Object get(int index)
    {
        return delegate.get(index);
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
     * Method to the position of an element.
     * @param element The element.
     * @return The position.
     **/
    public int indexOf(Object element)
    {
        return delegate.indexOf(element);
    }

    /**
     * Method to the position of an element.
     * @param element The element.
     * @param startIndex The start position
     * @return The position.
     **/
    public int indexOf(Object element, int startIndex)
    {
        return delegate.indexOf(element, startIndex);
    }

    /**
     * Accessor for whether the Vector is empty.
     * @return Whether it is empty.
     **/
    public boolean isEmpty()
    {
        return delegate.isEmpty();
    }

    /**
     * Method to retrieve an iterator for the list.
     * @return The iterator
     **/
    public Iterator iterator()
    {
        return new SCOListIterator(this, ownerSM, delegate, null, true, -1);
    }

    /**
     * Method to return the last element in the Vector.
     * @return The last element
     */
    public Object lastElement()
    {
        return delegate.lastElement();
    }

    /**
     * Method to retrieve the last position of the element.
     * @param element The element
     * @return The last position of this element in the List.
     **/
    public int lastIndexOf(Object element)
    {
        return delegate.lastIndexOf(element);
    }

    /**
     * Method to retrieve the last position of the element.
     * @param element The element
     * @param startIndex The start position
     * @return The last position of this element in the List.
     **/
    public int lastIndexOf(Object element, int startIndex)
    {
        return delegate.lastIndexOf(element, startIndex);
    }

    /**
     * Method to retrieve a List iterator for the list.
     * @return The iterator
     **/
    public ListIterator listIterator()
    {
        return new SCOListIterator(this, ownerSM, delegate, null, true, -1);
    }

    /**
     * Method to retrieve a List iterator for the list from the index.
     * @param index The start point 
     * @return The iterator
     **/
    public ListIterator listIterator(int index)
    {
        return new SCOListIterator(this, ownerSM, delegate, null, true, index);
    }

    /**
     * Accessor for the size of the Vector.
     * @return The size.
     **/
    public int size()
    {
        return delegate.size();
    }

    /**
     * Accessor for the subList of elements between from and to of the List
     * @param from Start index (inclusive)
     * @param to End index (exclusive) 
     * @return The subList
     **/
    public synchronized java.util.List subList(int from,int to)
    {
        return delegate.subList(from,to);
    }

    /**
     * Method to return the list as an array.
     * @return The array
     **/
    public synchronized Object[] toArray()
    {
        return delegate.toArray();
    }

    /**
     * Method to return the list as an array.
     * @param a The runtime types of the array being defined by this param
     * @return The array
     **/
    public synchronized Object[] toArray(Object a[])
    {
        return delegate.toArray(a);
    }

    /**
     * Method to add an element to a position in the Vector.
     * @param index The position
     * @param element The new element
     */
    public void add(int index, Object element)
    {
        delegate.add(index, element);
        makeDirty();
    }

    /**
     * Method to add an element to the Vector.
     * @param element The new element
     * @return Whether it was added ok.
     */
    public boolean add(Object element)
    {
        boolean success = delegate.add(element);
        if (success)
        {
            makeDirty();
        }
        return success;
    }

    /**
     * Method to add a Collection to the Vector.
     * @param elements The collection
     * @return Whether it was added ok.
     **/
    public boolean addAll(Collection elements)
    {
        boolean success = delegate.addAll(elements);
        if (success)
        {
            makeDirty();
        }
        return success;
    }

    /**
     * Method to add a Collection to a position in the Vector.
     * @param index Position to insert the collection.
     * @param elements The collection
     * @return Whether it was added ok.
     **/
    public boolean addAll(int index, Collection elements)
    {
        boolean success = delegate.addAll(index, elements);
        if (success)
        {
            makeDirty();
        }
        return success;
    }

    /**
     * Method to add an element to the Vector.
     * @param element The new element
     */
    public void addElement(Object element)
    {
        add(element);
    }

    /**
     * Method to clear the Vector.
     */
    public synchronized void clear()
    {
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
     */
    public synchronized boolean remove(Object element)
    {
        return remove(element, true);
    }

    /**
     * Method to remove an element from the List
     * @param element The Element to remove
     * @return Whether it was removed successfully.
     */
    public synchronized boolean remove(Object element, boolean allowCascadeDelete)
    {
        boolean success = delegate.remove(element);

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
     * Method to remove an element from the Vector.
     * @param index The element position.
     * @return The object that was removed
     */
    public Object remove(int index)
    {
        Object element = delegate.remove(index);

        if (ownerSM != null)
        {
            // Cascade delete
            AbstractMemberMetaData mmd = ownerSM.getClassMetaData().getMetaDataForMember(fieldName);
            if (SCOUtils.hasDependentElement(mmd))
            {
                ownerSM.getExecutionContext().deleteObjectInternal(element);
            }
        }

        makeDirty();
        return element;
    }

    /**
     * Method to remove a Collection of elements from the Vector.
     * @param elements The collection
     * @return Whether it was removed ok.
     */
    public boolean removeAll(Collection elements)
    {
        boolean success = delegate.removeAll(elements);

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
     * Method to remove an element from the Vector.
     * @param element The element
     * @return Whether the element was removed
     */
    public boolean removeElement(Object element)
    {
        return remove(element);
    }

    /**
     * Method to remove an element from the Vector.
     * @param index The element position.
     */
    public void removeElementAt(int index)
    {
        remove(index);
    }

    /**
     * Method to remove all elements from the Vector.
     */
    public void removeAllElements()
    {
        clear();
    }

    /**
     * Method to retain a Collection of elements (and remove all others).
     * @param c The collection to retain
     * @return Whether they were retained successfully.
     */
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
     * Wrapper addition that allows turning off of the dependent-field checks
     * when doing the position setting. This means that we can prevent the deletion of
     * the object that was previously in that position. This particular feature is used
     * when attaching a list field and where some elements have changed positions.
     * @param index The position
     * @param element The new element
     * @return The element previously at that position
     */
    public Object set(int index, Object element, boolean allowDependentField)
    {
        Object obj = delegate.set(index, element);
        makeDirty();
        return obj;
    }

    /**
     * Method to set the element at a position in the Vector.
     * @param index The position
     * @param element The new element
     * @return The element previously at that position
     **/
    public Object set(int index,Object element)
    {
        return set(index, element, true);
    }

    /**
     * Method to set the element at a position in the Vector.
     * @param element The new element
     * @param index The position
     **/
    public void setElementAt(Object element,int index)
    {
        // This is a historical wrapper to the Collection method
        set(index,element);
    }

    /**
     * The writeReplace method is called when ObjectOutputStream is preparing
     * to write the object to the stream. The ObjectOutputStream checks whether
     * the class defines the writeReplace method. If the method is defined, the
     * writeReplace method is called to allow the object to designate its
     * replacement in the stream. The object returned should be either of the
     * same type as the object passed in or an object that when read and
     * resolved will result in an object of a type that is compatible with 
     * all references to the object.
     *
     * @return the replaced object
     * @throws ObjectStreamException
     */
    protected Object writeReplace() throws ObjectStreamException
    {
        return new java.util.Vector(delegate);
    }
}