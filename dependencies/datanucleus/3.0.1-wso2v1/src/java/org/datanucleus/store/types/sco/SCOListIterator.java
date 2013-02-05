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
package org.datanucleus.store.types.sco;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.scostore.ListStore;

/**
 * An iterator for a SCO List object. Operates from either a delegate or a backing store, and provides 
 * iteration through the objects.
 */
public class SCOListIterator implements ListIterator
{
    private final ListIterator iter;

    /** The owning SCO that we are really iterating. */
    private final List ownerSCO;

    /** Whether the most recent access operation was a previous() */
    private boolean reverse;

    /**
     * Constructor taking the delegate or backing store, and any start index.
     * @param sco Owner SCO 
     * @param sm State Manager of SCO List to iterate 
     * @param theDelegate The delegate list
     * @param theStore The backing store (connected to the DB)
     * @param useDelegate whether to use a delegate
     * @param startIndex The start index position (any value below 0 will mean start at index 0).
     */
    public SCOListIterator(List sco, ObjectProvider sm, List theDelegate, ListStore theStore, boolean useDelegate,
            int startIndex)
    {
        ownerSCO = sco;

        // Populate our entries list
        ArrayList entries = new ArrayList();

        Iterator i=null;
        if (useDelegate)
        {
            i = theDelegate.iterator();
        }
        else
        {
            if (theStore != null)
            {
                i = theStore.iterator(sm);
            }
            else
            {
                i = theDelegate.iterator();
            }
        }
        while (i.hasNext())
        {
            entries.add(i.next());
        }

        if (startIndex >= 0)
        {
            iter = entries.listIterator(startIndex);
        }
        else
        {
            iter = entries.listIterator();
        }
    }

    public void add(Object o)
    {
        iter.add(o);
        ownerSCO.add(iter.previousIndex(), o);
    }

    public boolean hasNext()
    {
        return iter.hasNext();
    }

    public boolean hasPrevious()
    {
        return iter.hasPrevious();
    }

    public Object next()
    {
        Object result = iter.next();
        reverse = false;
        return result;
    }

    public int nextIndex()
    {
        return iter.nextIndex();
    }

    public Object previous()
    {
        Object result = iter.previous();
        reverse = true;
        return result;
    }

    public int previousIndex()
    {
        return iter.previousIndex();
    }

    public void remove()
    {
        iter.remove();
        ownerSCO.remove(iter.nextIndex());
    }

    public void set(Object o)
    {
        iter.set(o);

        /*
         * (java.util.ListIterator API docs)
         * "Note that the remove() and set(Object) methods are not defined in terms of the cursor position;
         * they are defined to operate on the last element returned by a call to next() or previous()."
         */
        ownerSCO.set(reverse ? iter.nextIndex() : iter.previousIndex(), o);
    }
}