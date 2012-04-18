/**********************************************************************
Copyright (c) 2006 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.store.fieldmanager;

import org.datanucleus.FetchPlanForClass;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.MetaDataUtils;
import org.datanucleus.state.FetchPlanState;
import org.datanucleus.store.ObjectProvider;

/**
 * Base field manager for handling the fetching of fields.
 * Supports a FetchPlan to navigate down an object graph.
 * This is extended by specific managers for the detachment and makeTransient processes.
 */
public abstract class AbstractFetchFieldManager extends AbstractFieldManager
{
    /** StateManager of the instance being fetched (detached or made transient). **/
    protected final ObjectProvider sm;

    /** Second class mutable fields for the class of this object. */
    protected final boolean[] secondClassMutableFields;

    /** Fetch Plan for the class of this object. */
    protected FetchPlanForClass fpClass;

    /** State for the fetch process. */
    protected final FetchPlanState state;

    /**
     * Exception thrown when we reach the end of the fetch depth in a branch of the object graph
     */
    public static class EndOfFetchPlanGraphException extends RuntimeException
    {
    }

    /**
     * Constructor for a field manager for fetch plan processing.
     * @param sm the StateManager of the instance being processed.
     * @param secondClassMutableFields
     * @param fpClass Fetch Plan for the class of this instance
     * @param state Object containing the state of the fetch process
     */
    public AbstractFetchFieldManager(ObjectProvider sm, boolean[] secondClassMutableFields, FetchPlanForClass fpClass, FetchPlanState state)
    {
        this.sm = sm;
        this.secondClassMutableFields = secondClassMutableFields;
        this.fpClass = fpClass;
        this.state = state;
    }

    /**
     * Method to fetch an object field whether it is SCO collection, PC, or whatever.
     * @param fieldNumber Number of the field
     * @return The object
     */
    public Object fetchObjectField(int fieldNumber)
    throws EndOfFetchPlanGraphException
    {
        AbstractMemberMetaData fmd = fpClass.getAbstractClassMetaData().getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
        final boolean requiresFCOFetching = MetaDataUtils.getInstance().storesFCO(fmd, sm.getExecutionContext());
        final int maxFetchDepth = fpClass.getFetchPlan().getMaxFetchDepth();
        final int currentFetchDepth = state.getCurrentFetchDepth();

        if (requiresFCOFetching)
        {
            if (currentFetchDepth > 0 && maxFetchDepth > 0 && currentFetchDepth == maxFetchDepth)
            {
                // Reached the overall max fetch depth so jump out
                return endOfGraphOperation(fieldNumber);
            }

            // Retrieve the max recurse depth for this field type
            final int maxRecursiveDepth = fpClass.getMaxRecursionDepthForMember(fieldNumber);
            if (maxRecursiveDepth > 0)
            {
                // We have a limit on recursing so check if the recursive depth is reached for this type
                if (state.getObjectDepthForType(fmd.getFullFieldName()) >= maxRecursiveDepth)
                {
                    return endOfGraphOperation(fieldNumber);
                }
            }

            // Add this field to the object graph, and set the current depth
            state.addMemberName(fmd.getFullFieldName());

            // Process the field
            Object result = internalFetchObjectField(fieldNumber);

            // Returned from a search down this branch so remove the field and update the current depth
            state.removeLatestMemberName();

            return result;
        }
        else
        {
            // Perform the fetch of this field
            return internalFetchObjectField(fieldNumber);
        }
    }

    /**
     * Method to fetch an object field whether it is SCO collection, PC, or whatever.
     * @param fieldNumber Number of the field
     * @return The object
     */
    protected abstract Object internalFetchObjectField(int fieldNumber);

    /**
     * Method called when an end of graph is encountered.
     * @param fieldNumber Number of the field
     * @return Object to return
     */
    protected abstract Object endOfGraphOperation(int fieldNumber);

    /* (non-Javadoc)
     * @see FieldSupplier#fetchBooleanField(int)
     */
    public boolean fetchBooleanField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchBooleanField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchByteField(int)
     */
    public byte fetchByteField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchByteField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchCharField(int)
     */
    public char fetchCharField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchCharField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchDoubleField(int)
     */
    public double fetchDoubleField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchDoubleField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchFloatField(int)
     */
    public float fetchFloatField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchFloatField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchIntField(int)
     */
    public int fetchIntField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchIntField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchLongField(int)
     */
    public long fetchLongField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchLongField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchShortField(int)
     */
    public short fetchShortField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchShortField(fieldNumber);
    }

    /* (non-Javadoc)
     * @see FieldSupplier#fetchStringField(int)
     */
    public String fetchStringField(int fieldNumber)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sm.provideFields(new int[]{fieldNumber}, sfv);
        return sfv.fetchStringField(fieldNumber);
    }
}