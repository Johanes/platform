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
package org.datanucleus.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ObjectManager;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.Relation;
import org.datanucleus.store.ExecutionContext;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.types.sco.SCOCollection;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.StringUtils;

/**
 * Manager for relationships of a class.
 * Performs management of (bidirectional) relations.
 * If one side is set yet the other isn't, corrects the other side.
 */
public class RelationshipManagerImpl implements RelationshipManager
{
    /** Localiser for messages. */
    protected static final Localiser LOCALISER = Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /** StateManager for the object we are managing the relationships for. */
    final StateManager ownerSM;

    final ExecutionContext ec;

    /** Object being managed. */
    final Object pc;

    /**
     * Map of bidirectional field "changes". 
     * For 1-1, N-1 fields the "change" is actually the original value (for later comparison).
     * For 1-N, M-N fields the "change" is an ArrayList of RelationChange objects.
     * TODO Change 1-1 to use RelationChange and have old+new value defined
     */
    final Map<Integer, Object> fieldChanges;

    /**
     * Constructor.
     * @param sm StateManager for the object that we are managing relations for.
     */
    public RelationshipManagerImpl(StateManager sm)
    {
        this.ownerSM = sm;
        this.ec = sm.getExecutionContext();
        this.pc = sm.getObject();
        this.fieldChanges = new HashMap();
    }

    private enum ChangeType
    {
        ADD_OBJECT, // Element added to a collection
        REMOVE_OBJECT, // Element removed from a collection
        CHANGE_OBJECT // Field value set (1-1, N-1)
    }

    /**
     * Definition of a change in a relation.
     */
    private static class RelationChange
    {
        ChangeType type;
        Object value;
        Object oldValue;
        public RelationChange(ChangeType type, Object val)
        {
            this.type = type;
            this.value = val;
        }
        @SuppressWarnings("unused")
        public RelationChange(ChangeType type, Object val, Object oldVal)
        {
            this.type = type;
            this.value = val;
            this.oldValue = oldVal;
        }
        public String toString()
        {
            if (oldValue != null)
            {
                return "RelationChange type=" + type + " value=" + StringUtils.toJVMIDString(oldValue) + 
                    " -> " + StringUtils.toJVMIDString(value);
            }
            else
            {
                return "RelationChange type=" + type + " value=" + StringUtils.toJVMIDString(value);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.state.RelationshipManager#clearFields()
     */
    public void clearFields()
    {
        fieldChanges.clear();
    }

    /* (non-Javadoc)
     * @see org.datanucleus.state.RelationshipManager#relationChange(int, java.lang.Object, java.lang.Object)
     */
    public void relationChange(int fieldNumber, Object oldValue, Object newValue)
    {
        if (ownerSM.getObjectManager().isManagingRelations())
        {
            return;
        }

        Integer fieldKey = Integer.valueOf(fieldNumber);
        AbstractClassMetaData cmd = ownerSM.getClassMetaData();
        AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
        int relationType = mmd.getRelationType(ownerSM.getObjectManager().getClassLoaderResolver());
        if (relationType == Relation.ONE_TO_ONE_BI || relationType == Relation.MANY_TO_ONE_BI)
        {
            if (!fieldChanges.containsKey(fieldKey))
            {
                // Only allow for set of PC field (ignore set of Collection fields)
                fieldChanges.put(fieldKey, oldValue);
            }
            return;
        }

        List<RelationChange> changes = (List<RelationChange>)fieldChanges.get(fieldKey);
        if (changes == null)
        {
            changes = new ArrayList<RelationChange>();
            fieldChanges.put(fieldKey, changes);
        }

        if (relationType == Relation.ONE_TO_MANY_BI || relationType == Relation.MANY_TO_MANY_BI)
        {
            if (mmd.hasCollection())
            {
                if (oldValue == null)
                {
                    if (newValue != null)
                    {
                        // Add all elements
                        Iterator iter = ((Collection)newValue).iterator();
                        while (iter.hasNext())
                        {
                            changes.add(new RelationChange(ChangeType.ADD_OBJECT, iter.next()));
                        }
                    }
                }
                else
                {
                    if (newValue == null)
                    {
                        AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(ownerSM.getObjectManager().getClassLoaderResolver())[0];
                        Iterator iter = ((Collection)oldValue).iterator();
                        while (iter.hasNext())
                        {
                            Object element = iter.next();
                            if (ownerSM.getLifecycleState().isDeleted)
                            {
                                // Deleting the owner, so register the element to reset its owner
                                ownerSM.getObjectManager().removeObjectFromLevel2Cache(ownerSM.getObjectManager().getApiAdapter().getIdForObject(element));
                                ObjectProvider elementSM = ownerSM.getObjectManager().findObjectProvider(element);
                                if (relationType == Relation.ONE_TO_MANY_BI)
                                {
                                    ec.getRelationshipManager(elementSM).relationChange(relatedMmd.getAbsoluteFieldNumber(), ownerSM.getObject(), null);
                                }
                                else if (relationType == Relation.MANY_TO_MANY_BI)
                                {
                                    ec.getRelationshipManager(elementSM).relationRemove(relatedMmd.getAbsoluteFieldNumber(), ownerSM.getObject());
                                }
                            }
                            else
                            {
                                // Remove the element
                                changes.add(new RelationChange(ChangeType.REMOVE_OBJECT, element));
                            }
                        }
                    }
                    else
                    {
                        // Remove some and add some
                        Iterator newIter = ((Collection)newValue).iterator();
                        while (newIter.hasNext())
                        {
                            Object newElem = newIter.next();
                            Iterator oldIter = ((Collection)oldValue).iterator();
                            boolean alreadyExists = false;
                            while (oldIter.hasNext())
                            {
                                Object oldElem = oldIter.next();
                                if (newElem == oldElem)
                                {
                                    alreadyExists = true;
                                    break;
                                }
                            }
                            if (!alreadyExists)
                            {
                                ObjectProvider elemSM = ownerSM.getObjectManager().findObjectProvider(newElem);
                                if (elemSM != null)
                                {
                                    AbstractMemberMetaData elemMmd = mmd.getRelatedMemberMetaData(ownerSM.getObjectManager().getClassLoaderResolver())[0];
                                    Object oldOwner = elemSM.provideField(elemMmd.getAbsoluteFieldNumber());
                                    if (!elemSM.isFieldLoaded(elemMmd.getAbsoluteFieldNumber()))
                                    {
                                        elemSM.loadField(elemMmd.getAbsoluteFieldNumber());
                                    }
                                    if (oldOwner != null)
                                    {
                                        // Remove from old owner collection
                                        ObjectProvider oldOwnerSM = ownerSM.getObjectManager().findObjectProvider(oldOwner);
                                        if (oldOwnerSM != null)
                                        {
                                            ec.getRelationshipManager(oldOwnerSM).relationRemove(fieldNumber, newElem);
                                        }
                                    }
                                }
                                relationAdd(fieldNumber, newElem);
                            }
                        }
                        Iterator oldIter = ((Collection)oldValue).iterator();
                        while (oldIter.hasNext())
                        {
                            Object oldElem = oldIter.next();
                            newIter = ((Collection)newValue).iterator();
                            boolean stillExists = false;
                            while (newIter.hasNext())
                            {
                                Object newElem = newIter.next();
                                if (oldElem == newElem)
                                {
                                    stillExists = true;
                                    break;
                                }
                            }
                            if (!stillExists)
                            {
                                relationRemove(fieldNumber, oldElem);
                            }
                        }
                    }
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.state.RelationshipManager#relationAdd(int, java.lang.Object)
     */
    public void relationAdd(int fieldNumber, Object val)
    {
        if (ownerSM.getObjectManager().isManagingRelations())
        {
            return;
        }

        AbstractClassMetaData cmd = ownerSM.getClassMetaData();
        AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
        int relationType = mmd.getRelationType(ownerSM.getObjectManager().getClassLoaderResolver());
        if (relationType != Relation.ONE_TO_MANY_BI && relationType != Relation.MANY_TO_MANY_BI)
        {
            return;
        }

        ObjectProvider elemSM = ownerSM.getObjectManager().findObjectProvider(val);
        if (elemSM != null)
        {
            AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(ownerSM.getObjectManager().getClassLoaderResolver())[0];
            if (elemSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
            {
                Object currentOwnerId = ownerSM.getObjectManager().getApiAdapter().getIdForObject(elemSM.provideField(relatedMmd.getAbsoluteFieldNumber()));
                ownerSM.getObjectManager().removeObjectFromLevel2Cache(currentOwnerId);
            }
        }

        Integer fieldKey = Integer.valueOf(fieldNumber);
        Object changes = fieldChanges.get(fieldKey);
        ArrayList changeList = null;
        if (changes == null)
        {
            changeList = new ArrayList();
        }
        else
        {
            // Assume we have an ArrayList
            changeList = (ArrayList)changes;
        }
        RelationChange change = new RelationChange(ChangeType.ADD_OBJECT, val);
        ownerSM.getObjectManager().removeObjectFromLevel2Cache(ownerSM.getObjectManager().getApiAdapter().getIdForObject(val));
        changeList.add(change);
        fieldChanges.put(fieldKey, changeList);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.state.RelationshipManager#relationRemove(int, java.lang.Object)
     */
    public void relationRemove(int fieldNumber, Object val)
    {
        if (ownerSM.getObjectManager().isManagingRelations())
        {
            return;
        }

        AbstractClassMetaData cmd = ownerSM.getClassMetaData();
        AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
        int relationType = mmd.getRelationType(ownerSM.getObjectManager().getClassLoaderResolver());
        if (relationType != Relation.ONE_TO_MANY_BI && relationType != Relation.MANY_TO_MANY_BI)
        {
            return;
        }

        Integer fieldKey = Integer.valueOf(fieldNumber);
        Object changes = fieldChanges.get(fieldKey);
        ArrayList changeList = null;
        if (changes == null)
        {
            changeList = new ArrayList();
            fieldChanges.put(fieldKey, changeList);
        }
        else
        {
            changeList = (ArrayList)changes;
        }
        RelationChange change = new RelationChange(ChangeType.REMOVE_OBJECT, val);
        ownerSM.getObjectManager().removeObjectFromLevel2Cache(ownerSM.getObjectManager().getApiAdapter().getIdForObject(val));
        changeList.add(change);
    }

    /**
     * Accessor for whether a field is being managed.
     * @param fieldNumber Number of the field
     * @return Whether it is currently managed
     */
    public boolean managesField(int fieldNumber)
    {
        return fieldChanges.containsKey(Integer.valueOf(fieldNumber));
    }

    /* (non-Javadoc)
     * @see org.datanucleus.state.RelationshipManager#checkConsistency()
     */
    public void checkConsistency()
    {
        Set entries = fieldChanges.entrySet();
        Iterator iter = entries.iterator();
        AbstractClassMetaData cmd = ownerSM.getClassMetaData();
        ObjectManager om = ownerSM.getObjectManager();
        while (iter.hasNext())
        {
            Map.Entry entry = (Map.Entry)iter.next();
            int fieldNumber = ((Integer)entry.getKey()).intValue();
            AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
            ClassLoaderResolver clr = om.getClassLoaderResolver();
            Object oldValue = entry.getValue();
            int relationType = mmd.getRelationType(clr);
            if (relationType == Relation.ONE_TO_ONE_BI)
            {
                // 1-1 bidirectional
                Object newValue = ownerSM.provideField(fieldNumber);
                checkOneToOneBidirectionalRelation(mmd, clr, om, oldValue, newValue);
            }
            else if (relationType == Relation.MANY_TO_ONE_BI)
            {
                // N-1 bidirectional
                Object newValue = ownerSM.provideField(fieldNumber);
                checkManyToOneBidirectionalRelation(mmd, clr, om, oldValue, newValue);
            }
            else if (relationType == Relation.ONE_TO_MANY_BI)
            {
                // 1-N bidirectional
                List changes = (List)oldValue;
                checkOneToManyBidirectionalRelation(mmd, clr, om, changes);
            }
            else if (relationType == Relation.MANY_TO_MANY_BI)
            {
                // M-N bidirectional
                List changes = (List)oldValue;
                checkManyToManyBidirectionalRelation(mmd, clr, om, changes);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.state.RelationshipManager#process()
     */
    public void process()
    {
        Set entries = fieldChanges.entrySet();
        Iterator iter = entries.iterator();
        AbstractClassMetaData cmd = ownerSM.getClassMetaData();
        ObjectManager om = ownerSM.getObjectManager();
        while (iter.hasNext())
        {
            Map.Entry entry = (Map.Entry)iter.next();
            int fieldNumber = ((Integer)entry.getKey()).intValue();
            AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
            ClassLoaderResolver clr = om.getClassLoaderResolver();
            Object oldValue = entry.getValue();
            int relationType = mmd.getRelationType(clr);
            if (relationType == Relation.ONE_TO_ONE_BI)
            {
                // 1-1 bidirectional
                Object newValue = ownerSM.provideField(fieldNumber);
                processOneToOneBidirectionalRelation(mmd, clr, om, oldValue, newValue);
            }
            else if (relationType == Relation.MANY_TO_ONE_BI)
            {
                // N-1 bidirectional
                Object newValue = ownerSM.provideField(fieldNumber);
                processManyToOneBidirectionalRelation(mmd, clr, om, oldValue, newValue);
            }
            else if (relationType == Relation.ONE_TO_MANY_BI)
            {
                // 1-N bidirectional
                List changes = (List)oldValue;
                processOneToManyBidirectionalRelation(mmd, clr, om, changes);
            }
            else if (relationType == Relation.MANY_TO_MANY_BI)
            {
                // M-N bidirectional
                List changes = (List)oldValue;
                processManyToManyBidirectionalRelation(mmd, clr, om, changes);
            }
        }
    }

    /**
     * Method to check the consistency of the passed field as 1-1.
     * Processes the case where we had a 1-1 field set at this side previously to some value and now to
     * some other value. We need to make sure that all of the affected objects are now related consistently.
     * Taking an example <pre>a.b = b1; a.b = b2;</pre> so A's b field is changed from b1 to b2.
     * The following changes are likely to be necessary
     * <ul>
     * <li>b1.a = null - so we null out the old related objects link back to this object</li>
     * <li>b2.oldA = null - if b2 was previously related to a different A, null out that objects link to b2</li>
     * <li>b2.a = a - set the link from b2 back to a so it is bidirectional</li>
     * </ul>
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param oldValue The old value
     * @param newValue The new value
     */
    protected void checkOneToOneBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, Object oldValue, Object newValue)
    {
        if (newValue != null)
        {
            // Previously had "a.b = b1"; Now have "a.b = b2"
            // Check that the new value hasnt been assigned to something other than this object
            AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaDataForObject(clr, pc, newValue);
            ObjectProvider newSM = om.findObjectProvider(newValue);
            if (newSM != null && relatedMmd != null)
            {
                if (!newSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                {
                    // Load the field in case we need to set the old value
                    newSM.loadField(relatedMmd.getAbsoluteFieldNumber());
                }
                Object newValueFieldValue = newSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                if (newValueFieldValue != pc)
                {
                    RelationshipManager newRelMgr = ec.getRelationshipManager(newSM);
                    if (newRelMgr != null && newRelMgr.managesField(relatedMmd.getAbsoluteFieldNumber()))
                    {
                        // New value has had its side of the relation changed to a different value altogether!
                        if (newValueFieldValue == null)
                        {
                            String msg = LOCALISER.msg("013003",
                                StringUtils.toJVMIDString(pc), mmd.getName(),
                                StringUtils.toJVMIDString(newValue), relatedMmd.getName());
                            NucleusLogger.PERSISTENCE.error(msg);
                            throw new NucleusUserException(msg);
                        }
                        else
                        {
                            String msg = LOCALISER.msg("013002",
                                StringUtils.toJVMIDString(pc), mmd.getName(),
                                StringUtils.toJVMIDString(newValue), relatedMmd.getName(),
                                StringUtils.toJVMIDString(newValueFieldValue));
                            NucleusLogger.PERSISTENCE.error(msg);
                            throw new NucleusUserException(msg);
                        }
                    }
                }
            }
        }
    }

    /**
     * Method to check the consistency of the passed field as 1-N.
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param changes List of changes to the collection
     */
    protected void checkOneToManyBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, List<RelationChange> changes)
    {
        Iterator iter = changes.iterator();
        while (iter.hasNext())
        {
            RelationChange change = (RelationChange)iter.next();
            if (change.type == ChangeType.ADD_OBJECT)
            {
                if (ownerSM.getObjectManager().getApiAdapter().isDeleted(change.value))
                {
                    // The element was added but was then the element object was deleted!
                    throw new NucleusUserException(LOCALISER.msg("013008",
                        StringUtils.toJVMIDString(pc), mmd.getName(), StringUtils.toJVMIDString(change.value)));
                }
                else
                {
                    AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(clr)[0];
                    ObjectProvider newElementSM = om.findObjectProvider(change.value);
                    if (newElementSM != null)
                    {
                        if (newElementSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                        {
                            RelationshipManager newElementRelMgr = ec.getRelationshipManager(newElementSM);
                            if (newElementRelMgr != null && newElementRelMgr.managesField(relatedMmd.getAbsoluteFieldNumber()))
                            {
                                // Element has had the owner set, so make sure it is set to this object
                                Object newValueFieldValue = newElementSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                                if (newValueFieldValue != pc && newValueFieldValue != null)
                                {
                                    // The element has a different owner than the PC with this collection
                                    // This catches cases where the user has set the wrong owner, and also
                                    // will catch cases where the user has added it to two collections
                                    throw new NucleusUserException(LOCALISER.msg("013009",
                                        StringUtils.toJVMIDString(pc), mmd.getName(), 
                                        StringUtils.toJVMIDString(change.value), 
                                        StringUtils.toJVMIDString(newValueFieldValue)));
                                }
                            }
                        }
                    }
                }
            }
            else if (change.type == ChangeType.REMOVE_OBJECT)
            {
                if (ownerSM.getObjectManager().getApiAdapter().isDeleted(change.value))
                {
                    // The element was removed and was then the element object was deleted so do nothing
                }
                else
                {
                    AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(clr)[0];
                    ObjectProvider newElementSM = om.findObjectProvider(change.value);
                    if (newElementSM != null)
                    {
                        if (newElementSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                        {
                            RelationshipManager newElementRelMgr = ec.getRelationshipManager(newElementSM);
                            if (newElementRelMgr != null && newElementRelMgr.managesField(relatedMmd.getAbsoluteFieldNumber()))
                            {
                                // Element has had the owner set, so make sure it is not set to this object
                                Object newValueFieldValue = newElementSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                                if (newValueFieldValue == pc)
                                {
                                    // The element was removed from the collection, but was updated to have its owner
                                    // set to the collection owner!
                                    throw new NucleusUserException(LOCALISER.msg("013010",
                                        StringUtils.toJVMIDString(pc), mmd.getName(),
                                        StringUtils.toJVMIDString(change.value)));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Method to check the consistency of the passed field as N-1.
     * Processes the case where we had an N-1 field set at this side previously to some value and now to
     * some other value.That is, this object was in some collection/map originally, and now should be in some
     * other collection/map. So in terms of an example this object "a" was in collection "b1.as" before and is 
     * now in "b2.as". The following changes are likely to be necessary
     * <ul>
     * <li>b1.getAs().remove(a) - remove it from b1.as if still present</li>
     * <li>b2.getAs().add(a) - add it to b1.as if not present</li>
     * </ul>
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param oldValue The old value
     * @param newValue The new value
     */
    protected void checkManyToOneBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, Object oldValue, Object newValue)
    {
        // TODO Implement N-1
    }

    /**
     * Method to check consistency of the passed field as M-N.
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param changes List of changes to the collection
     */
    protected void checkManyToManyBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, List<RelationChange> changes)
    {
        // TODO Implement M-N
    }

    /**
     * Method to process all 1-1 bidir fields.
     * Processes the case where we had a 1-1 field set at this side previously to some value and now to
     * some other value. We need to make sure that all of the affected objects are now related consistently.
     * Taking an example <pre>a.b = b1; a.b = b2;</pre> so A's b field is changed from b1 to b2.
     * The following changes are likely to be necessary
     * <ul>
     * <li>b1.a = null - so we null out the old related objects link back to this object</li>
     * <li>b2.oldA = null - if b2 was previously related to a different A, null out that objects link to b2</li>
     * <li>b2.a = a - set the link from b2 back to a so it is bidirectional</li>
     * </ul>
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param oldValue The old value
     * @param newValue The new value
     */
    protected void processOneToOneBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, Object oldValue, Object newValue)
    {
        if (oldValue != null)
        {
            // Previously had "a.b = b1"; "a.b" has been changed
            // Need to remove from the other side if still set
            AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaDataForObject(clr, pc, oldValue);
            ObjectProvider oldSM = om.findObjectProvider(oldValue);
            if (oldSM != null)
            {
                boolean oldIsDeleted = om.getApiAdapter().isDeleted(oldSM.getObject());
                if (!oldIsDeleted)
                {
                    // Old still exists, so make sure its relation is correct
                    if (!oldSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                    {
                        // Load the field in case we need to set the old value
                        oldSM.loadField(relatedMmd.getAbsoluteFieldNumber());
                    }
                    Object oldValueFieldValue = oldSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                    if (oldValueFieldValue == null)
                    {
                        // Set to null so nothing to do
                    }
                    else if (oldValueFieldValue == pc)
                    {
                        // Still set to this object, so null out the other objects relation
                        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                        {
                            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("013004",
                                    StringUtils.toJVMIDString(oldValue), relatedMmd.getFullFieldName(),
                                    StringUtils.toJVMIDString(pc), StringUtils.toJVMIDString(newValue)));
                        }
                        oldSM.replaceFieldValue(relatedMmd.getAbsoluteFieldNumber(), null);
                    }
                }
                else
                {
                    ownerSM.replaceFieldValue(mmd.getAbsoluteFieldNumber(), null);
                }
            }
        }
        if (newValue != null)
        {
            // Previously had "a.b = b1"; Now have "a.b = b2"
            // Need to set the other side if not yet set, and unset any related old value on the other side
            AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaDataForObject(clr, pc, newValue);
            ObjectProvider newSM = om.findObjectProvider(newValue);
            if (newSM != null && relatedMmd != null)
            {
                if (!newSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                {
                    // Load the field in case we need to set the link from the old value
                    newSM.loadField(relatedMmd.getAbsoluteFieldNumber());
                }
                Object newValueFieldValue = newSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                if (newValueFieldValue == null)
                {
                    // Was set to null so set to our object
                    if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                    {
                        NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("013005",
                                StringUtils.toJVMIDString(newValue), relatedMmd.getFullFieldName(),
                                StringUtils.toJVMIDString(pc)));
                    }
                    newSM.replaceFieldValue(relatedMmd.getAbsoluteFieldNumber(), pc);
                }
                else if (newValueFieldValue != pc)
                {
                    // Was set to different object, so null out the other objects relation
                    ObjectProvider newValueFieldSM = om.findObjectProvider(newValueFieldValue);
                    if (newValueFieldSM != null)
                    {
                        // Null out the field of the related object of the new value
                        if (!newValueFieldSM.isFieldLoaded(mmd.getAbsoluteFieldNumber()))
                        {
                            // Load the field in case we need to set the link from the old value
                            newValueFieldSM.loadField(mmd.getAbsoluteFieldNumber());
                        }
                        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                        {
                            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("013004",
                                    StringUtils.toJVMIDString(newValueFieldValue), 
                                    mmd.getFullFieldName(),
                                    StringUtils.toJVMIDString(newValue), StringUtils.toJVMIDString(pc)));
                        }
                        newValueFieldSM.replaceFieldValue(mmd.getAbsoluteFieldNumber(), null);
                    }
                    // Update the field of the new value to our object
                    if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                    {
                        NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("013005",
                                StringUtils.toJVMIDString(newValue), relatedMmd.getFullFieldName(),
                                StringUtils.toJVMIDString(pc)));
                    }
                    newSM.replaceFieldValue(relatedMmd.getAbsoluteFieldNumber(), pc);
                }
            }
        }
    }

    /**
     * Method to process all 1-N bidirectional fields.
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param changes List of changes to the collection
     */
    protected void processOneToManyBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, List<RelationChange> changes)
    {
        // TODO A better way here would be to consider a particular element and if removed+added
        // then we should allow for that in updates. Also, what if an element is reassigned to a different
        // collection; this is not really allowed for here since we only consider one collection
        Iterator iter = changes.iterator();
        while (iter.hasNext())
        {
            RelationChange change = (RelationChange)iter.next();
            if (change.type == ChangeType.ADD_OBJECT)
            {
                ObjectProvider sm = om.findObjectProvider(change.value);
                if (sm != null)
                {
                    // make sure the link to the owner is now set
                    AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(clr)[0];
                    if (sm.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                    {
                        Object currentVal = sm.provideField(relatedMmd.getAbsoluteFieldNumber());
                        if (currentVal != ownerSM.getObject())
                        {
                            sm.replaceFieldValue(relatedMmd.getAbsoluteFieldNumber(), ownerSM.getObject());
                        }
                    }
                    else
                    {
                        ownerSM.getObjectManager().removeObjectFromLevel2Cache(sm.getInternalObjectId());
                    }
                }
            }
            else if (change.type == ChangeType.REMOVE_OBJECT)
            {
                ObjectProvider sm = om.findObjectProvider(change.value);
                if (sm != null)
                {
                    // make sure the link back to the owner is not still set
                    AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(clr)[0];
                    if (sm.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                    {
                        Object currentVal = sm.provideField(relatedMmd.getAbsoluteFieldNumber());
                        if (currentVal == ownerSM.getObject())
                        {
                            sm.replaceFieldValue(relatedMmd.getAbsoluteFieldNumber(), null);
                        }
                    }
                    else
                    {
                        ownerSM.getObjectManager().removeObjectFromLevel2Cache(sm.getInternalObjectId());
                    }
                }
            }
        }
    }

    /**
     * Method to process all N-1 bidirectional fields.
     * Processes the case where we had an N-1 field set at this side previously to some value and now to
     * some other value.That is, this object was in some collection/map originally, and now should be in some
     * other collection/map. So in terms of an example this object "a" was in collection "b1.as" before and is 
     * now in "b2.as". The following changes are likely to be necessary
     * <ul>
     * <li>b1.getAs().remove(a) - remove it from b1.as if still present</li>
     * <li>b2.getAs().add(a) - add it to b1.as if not present</li>
     * </ul>
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param oldValue The old value
     * @param newValue The new value
     */
    protected void processManyToOneBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, Object oldValue, Object newValue)
    {
        if (oldValue != null)
        {
            // Has been removed from a Collection/Map
            AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaDataForObject(clr, pc, oldValue);
            ObjectProvider oldSM = om.findObjectProvider(oldValue);
            if (oldSM != null && relatedMmd != null &&
                oldSM.getLoadedFields()[relatedMmd.getAbsoluteFieldNumber()])
            {
                if (oldSM.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                {
                    Object oldContainerValue = oldSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                    if (oldContainerValue instanceof Collection)
                    {
                        Collection oldColl = (Collection)oldContainerValue;
                        if (oldColl.contains(pc))
                        {
                            if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                            {
                                NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("013006",
                                        StringUtils.toJVMIDString(pc), mmd.getFullFieldName(),
                                        relatedMmd.getFullFieldName(), StringUtils.toJVMIDString(oldValue)));
                            }

                            if (oldColl instanceof SCOCollection)
                            {
                                // Avoid any cascade deletes that could have been fired by this action
                                ((SCOCollection)oldColl).remove(pc, false);
                            }
                            else
                            {
                                oldColl.remove(pc);
                            }
                        }
                    }
                }
            }
            else
            {
                if (oldSM != null)
                {
                    ownerSM.getObjectManager().removeObjectFromLevel2Cache(oldSM.getInternalObjectId());
                }
            }
        }

        if (newValue != null)
        {
            // Add new value to the Collection
            AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaDataForObject(clr, pc, newValue);
            ObjectProvider newSM = om.findObjectProvider(newValue);
            if (newSM != null && relatedMmd != null &&
                newSM.getLoadedFields()[relatedMmd.getAbsoluteFieldNumber()])
            {
                Object newContainerValue = newSM.provideField(relatedMmd.getAbsoluteFieldNumber());
                if (newContainerValue instanceof Collection)
                {
                    Collection newColl = (Collection)newContainerValue;
                    if (!newColl.contains(pc))
                    {
                        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                        {
                            NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("013007",
                                    StringUtils.toJVMIDString(pc), mmd.getFullFieldName(),
                                    relatedMmd.getFullFieldName(), StringUtils.toJVMIDString(newValue)));
                        }
                        newColl.add(pc);
                    }
                }
            }
            else
            {
                // Relation field not loaded so evict it from the L2 cache to avoid loading old field values
                ownerSM.getObjectManager().removeObjectFromLevel2Cache(
                    ownerSM.getObjectManager().getApiAdapter().getIdForObject(newValue));
            }
        }
    }

    /**
     * Method to process all M-N bidirectional fields.
     * @param mmd MetaData for the field
     * @param clr ClassLoader resolver
     * @param om ObjectManager
     * @param changes List of changes to the collection
     */
    protected void processManyToManyBidirectionalRelation(AbstractMemberMetaData mmd,
            ClassLoaderResolver clr, ObjectManager om, List<RelationChange> changes)
    {
        Iterator iter = changes.iterator();
        while (iter.hasNext())
        {
            RelationChange change = (RelationChange)iter.next();
            if (change.type == ChangeType.ADD_OBJECT)
            {
                ObjectProvider sm = om.findObjectProvider(change.value);
                if (sm != null)
                {
                    // make sure the element has the owner in its collection
                    AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(clr)[0];
                    ownerSM.getObjectManager().removeObjectFromLevel2Cache(sm.getInternalObjectId());
                    ownerSM.getObjectManager().removeObjectFromLevel2Cache(ownerSM.getInternalObjectId());
                    if (ownerSM.isFieldLoaded(mmd.getAbsoluteFieldNumber()) && !ownerSM.getLifecycleState().isDeleted)
                    {
                        Collection currentVal = (Collection)ownerSM.provideField(mmd.getAbsoluteFieldNumber());
                        if (currentVal != null && !currentVal.contains(sm.getObject()))
                        {
                            currentVal.add(sm.getObject());
                        }
                    }
                    if (sm.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()))
                    {
                        Collection currentVal = (Collection)sm.provideField(relatedMmd.getAbsoluteFieldNumber());
                        if (currentVal != null && !currentVal.contains(ownerSM.getObject()))
                        {
                            currentVal.add(ownerSM.getObject());
                        }
                    }
                }
            }
            else if (change.type == ChangeType.REMOVE_OBJECT)
            {
                ObjectProvider sm = om.findObjectProvider(change.value);
                if (sm != null)
                {
                    // make sure the element removes the owner from its collection
                    AbstractMemberMetaData relatedMmd = mmd.getRelatedMemberMetaData(clr)[0];
                    ownerSM.getObjectManager().removeObjectFromLevel2Cache(sm.getInternalObjectId());
                    ownerSM.getObjectManager().removeObjectFromLevel2Cache(ownerSM.getInternalObjectId());
                    if (ownerSM.isFieldLoaded(mmd.getAbsoluteFieldNumber()) && !ownerSM.getLifecycleState().isDeleted)
                    {
                        Collection currentVal = (Collection)ownerSM.provideField(mmd.getAbsoluteFieldNumber());
                        if (!sm.getLifecycleState().isDeleted && currentVal != null && currentVal.contains(sm.getObject()))
                        {
                            currentVal.remove(sm.getObject());
                        }
                        else
                        {
                            // element is deleted so can't call remove since it may try to read fields from it
                            // so just unload the collection in the owner forcing it to be reloaded from the DB
                            ownerSM.unloadField(mmd.getName());
                        }
                    }
                    if (sm.isFieldLoaded(relatedMmd.getAbsoluteFieldNumber()) && !sm.getLifecycleState().isDeleted)
                    {
                        Collection currentVal = (Collection)sm.provideField(relatedMmd.getAbsoluteFieldNumber());
                        if (currentVal != null && currentVal.contains(ownerSM.getObject()))
                        {
                            currentVal.remove(ownerSM.getObject());
                        }
                    }
                }
            }
        }
    }
}