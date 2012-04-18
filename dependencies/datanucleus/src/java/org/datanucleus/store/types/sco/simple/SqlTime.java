/**********************************************************************
Copyright (c) 2003 Andy Jefferson and others. All rights reserved.
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

import javax.jdo.spi.PersistenceCapable;

import org.datanucleus.state.FetchPlanState;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.types.sco.SCO;

/**
 * A mutable second-class SQLTime object.
 */
public class SqlTime extends java.sql.Time implements SCO
{
    protected transient Object owner;
    protected transient ObjectProvider ownerSM;
    protected transient String fieldName;

    /**
     * Creates a <tt>SqlTime</tt> object that represents the time at which it was allocated.
     * @param ownerSM the owning StateManager
     * @param fieldName the owning field name
     */
    public SqlTime(ObjectProvider ownerSM, String fieldName)
    {
        super(0);

        this.ownerSM = ownerSM;
        this.owner = ownerSM.getObject();
        this.fieldName = fieldName;
    }

    /**
     * Method to initialise the SCO for use.
     */
    public void initialise()
    {
    }

    /**
     * Method to initialise the SCO from an existing value.
     * @param o The Object
     * @param forInsert Whether the object needs inserting in the datastore with this value
     * @param forUpdate Whether to update the datastore with this value
     */
    public void initialise(Object o, boolean forInsert, boolean forUpdate)
    {
        super.setTime(((java.sql.Time)o).getTime());
    }

    /**
     * Accessor for the unwrapped value that we are wrapping.
     * @return The unwrapped value
     */
    public Object getValue()
    {
        return new java.sql.Time(getTime());
    }

    /**
     * Utility to unset the owner.
     **/
    public void unsetOwner()
    {
        ownerSM = null;
    }

    /**
     * Accessor for the owner.
     * @return The owner 
     **/
    public Object getOwner()
    {
        return (ownerSM != null ? ownerSM.getObject() : null);
    }

    /**
     * Accessor for the field name
     * @return The field name
     **/
    public String getFieldName()
    {
        return fieldName;
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
     * Method to detach a copy of this object.
     * @param state State for detachment process
     * @return The detached object
     */
    public Object detachCopy(FetchPlanState state)
    {
        return new java.sql.Time(getTime());
    }

    /**
     * Method to return an attached version for the passed StateManager and field, using the passed value.
     * @param value The new value
     */
    public void attachCopy(Object value)
    {
        long oldValue = getTime();
        initialise(value, false, true);

        // Check if the field has changed, and set the owner field as dirty if necessary
        long newValue = ((java.sql.Time)value).getTime();
        if (oldValue != newValue)
        {
            makeDirty();
        }
    }

    /**
     * Creates and returns a copy of this object.
     *
     * <P>Mutable second-class Objects are required to provide a public
     * clone method in order to allow for copying PersistenceCapable
     * objects. In contrast to Object.clone(), this method must not throw a
     * CloneNotSupportedException.
     * @return Clone of the object
     */
    public Object clone()
    {
        Object obj = super.clone();

        ((SqlTime)obj).unsetOwner();

        return obj;
    }

    /**
     * Sets the time of this <tt>Time</tt> object to the specified value.
     * This <tt>Time</tt> object is modified so that it represents a point in
     * time with the hour, minute, second as specified.
     *
     * @param   time   millisecs since 1 Jan 1970, 00:00:00 GMT
     * @see     java.util.Calendar
     */
    public void setTime(long time)
    {
        super.setTime(time);
        makeDirty();
    }

    /**
     * The writeReplace method is called when ObjectOutputStream is preparing to write the object to the stream. 
     * The ObjectOutputStream checks whether the class defines the writeReplace method. If the method is defined, 
     * the writeReplace method is called to allow the object to designate its replacement in the stream. The object 
     * returned should be either of the same type as the object passed in or an object that when read and resolved 
     * will result in an object of a type that is compatible with all references to the object.
     * @return the replaced object
     * @throws ObjectStreamException
     */
	protected Object writeReplace() throws ObjectStreamException
	{
		return new java.sql.Time(this.getTime());
	}     
}