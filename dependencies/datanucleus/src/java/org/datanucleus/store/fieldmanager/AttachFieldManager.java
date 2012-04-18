/**********************************************************************
Copyright (c) 2004 Erik Bengtson and others. All rights reserved. 
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
2005 Andy Jefferson - cater for object not being persistent
2007 Andy Jefferson - attach in-situ
2008 Andy Jefferson - loading of fields handling
    ...
**********************************************************************/
package org.datanucleus.store.fieldmanager;

import java.util.Collection;
import java.util.Map;

import org.datanucleus.api.ApiAdapter;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.store.ExecutionContext;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.types.sco.SCO;
import org.datanucleus.store.types.sco.SCOContainer;
import org.datanucleus.store.types.sco.SCOUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * Utility class to handle the attach of fields.
 * The attachment process has 2 distinct cases to cater for.
 * <OL>
 * <LI>The object was detached, has been updated, and needs reattaching.</LI>
 * <LI>The object was detached from a different datastore, and is being attached here and we want to
 *     do a pass through the object to update the fields in the object before it is persisted</LI>
 * </OL>
 * In the first case, the fields which are specified have their values (and dirty flags) updated.
 * In the second case, all fields have their fields (and dirty flags) updated.
 * In addition this field manager allows attaching a copy, or attaching in-situ.
 */
public class AttachFieldManager extends AbstractFieldManager
{
    /** Localiser for internationalisation. */
    protected static final Localiser LOCALISER = Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /** ObjectProvider for the attached instance */
    private final ObjectProvider attachedOP;

    /** The second class mutable fields. */
    private final boolean[] secondClassMutableFields;

    /** Fields that were marked as dirty at attach. */
    private final boolean dirtyFields[];

    /** Whether the attached instance is persistent yet. */
    private final boolean persistent;

    /** Whether to cascade the attach to related fields. */
    private final boolean cascadeAttach;

    /** Whether we should create attached copies, or attach in situ. */
    boolean copy = true;

    /**
     * Constructor.
     * @param attachedOP ObjectProvider for the attached instance
     * @param secondClassMutableFields second class mutable field flags
     * @param dirtyFields Flags for whether the field(s) are dirty
     * @param persistent whether the object being "attached" is persistent (yet)
     * @param cascadeAttach Whether to cascade any attach calls to related fields
     */
    public AttachFieldManager(ObjectProvider attachedOP, boolean[] secondClassMutableFields, 
            boolean[] dirtyFields, boolean persistent, boolean cascadeAttach, boolean copy)
    {
        this.attachedOP = attachedOP;
        this.secondClassMutableFields = secondClassMutableFields;
        this.dirtyFields = dirtyFields;
        this.persistent = persistent;
        this.cascadeAttach = cascadeAttach;
        this.copy = copy;
    }

    /**
     * Method to store an object field into the attached instance.
     * @param fieldNumber Number of the field to store
     * @param value the value in the detached instance
     */
    public void storeObjectField(int fieldNumber, Object value)
    {
        // Note : when doing updates always do replaceField first and makeDirty after since the replaceField
        // can cause flush() to be called meaning that an update with null would be made before the new value
        // makes it into the field
        AbstractClassMetaData cmd = attachedOP.getClassMetaData();
        AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);

//        boolean processWhenExisting = true;
        if (mmd.hasExtension("attach"))
        {
            if (mmd.getValueForExtension("attach").equalsIgnoreCase("never"))
            {
                // Member is tagged to not attach, so put a null
                attachedOP.replaceFieldMakeDirty(fieldNumber, null);
                return;
            }
            // TODO Support attach only when not present in the datastore (only for PCs)
//            else if (mmd.getValueForExtension("attach").equalsIgnoreCase("when-non-existing"))
//            {
//                processWhenExisting = false;
//            }
        }

        ExecutionContext ec = attachedOP.getExecutionContext();
        ApiAdapter api = ec.getApiAdapter();
        if (value == null)
        {
            Object oldValue = null;
            if (mmd.isDependent() && persistent)
            {
                // Get any old value of this field so we can do cascade-delete if being nulled
                try
                {
                    attachedOP.loadFieldFromDatastore(fieldNumber);
                }
                catch (Exception e)
                {
                    // Error loading the field so didn't exist before attaching anyway
                }
                oldValue = attachedOP.provideField(fieldNumber);
            }

            attachedOP.replaceField(fieldNumber, null);
            if (dirtyFields[fieldNumber] || !persistent)
            {
                attachedOP.makeDirty(fieldNumber);
            }

            if (mmd.isDependent() && !mmd.isEmbedded() &&
                oldValue != null && value == null && api.isPersistable(oldValue))
            {
                // Check for a field storing a PC where it is being nulled and the other object is dependent
                attachedOP.flush(); // Flush the nulling of the field
                NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("026026", oldValue, mmd.getFullFieldName()));
                ec.deleteObjectInternal(oldValue);
            }
        }
        else if (secondClassMutableFields[fieldNumber])
        {
            if (mmd.isSerialized())
            {
                // SCO Field is serialised so just update the column with this new value
                attachedOP.replaceFieldMakeDirty(fieldNumber, value);
                attachedOP.makeDirty(fieldNumber);
            }
            else
            {
                // Make sure that the value is a SCO wrapper
                Object oldValue = attachedOP.provideField(fieldNumber);
                if (oldValue == null && !attachedOP.getLoadedFields()[fieldNumber] && persistent)
                {
                    // Retrieve old value for field
                    attachedOP.loadField(fieldNumber);
                    oldValue = attachedOP.provideField(fieldNumber);
                }

                SCO sco;
                if (oldValue == null || (oldValue != null && !(oldValue instanceof SCO)))
                {
                    // Detached object didn't use wrapped field
                    if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                    {
                        NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("026029", StringUtils.toJVMIDString(attachedOP.getObject()),
                            attachedOP.getInternalObjectId(), mmd.getName()));
                    }
                    sco = SCOUtils.newSCOInstance(attachedOP, mmd, mmd.getType(), null, null, false, false, false);
                    if (sco instanceof SCOContainer)
                    {
                        // Load any containers to avoid update issues
                        ((SCOContainer)sco).load();
                    }
                    attachedOP.replaceFieldMakeDirty(fieldNumber, sco);
                }
                else
                {
                    // The field is already a SCO wrapper, so just copy the new values to it
                    sco = (SCO) oldValue;
                }

                if (cascadeAttach)
                {
                    // Only trigger the cascade when required
                    if (copy)
                    {
                        // Attach copy of the SCO
                        sco.attachCopy(value);
                    }
                    else
                    {
                        // Attach SCO in-situ
                        // TODO This doesn't seem to handle any removal of things while detached
                        // Should be changed to do things like in attachCopy above.
                        if (sco instanceof Collection)
                        {
                            // Attach all PC elements of the collection
                            SCOUtils.attachForCollection(attachedOP, ((Collection)value).toArray(),
                                SCOUtils.collectionHasElementsWithoutIdentity(mmd));
                        }
                        else if (sco instanceof Map)
                        {
                            // Attach all PC keys/values of the map
                            SCOUtils.attachForMap(attachedOP, ((Map)value).entrySet(), 
                                SCOUtils.mapHasKeysWithoutIdentity(mmd), 
                                SCOUtils.mapHasValuesWithoutIdentity(mmd));
                        }
                        else
                        {
                            // Initialise the SCO with the new value
                            sco.initialise(value, false, false);
                        }
                    }
                }

                if (dirtyFields[fieldNumber] || !persistent)
                {
                    attachedOP.makeDirty(fieldNumber);
                }
            }
        }
        else if (api.isPersistable(value))
        {
            // Field containing PC object
            ObjectProvider valueSM = ec.findObjectProvider(value);
            if (valueSM != null && valueSM.getReferencedPC() != null && !api.isPersistent(value))
            {
                // Value has StateManager and has referenced object so is being attached, so refer to attached PC
                attachedOP.replaceFieldMakeDirty(fieldNumber, valueSM.getReferencedPC());
            }

            if (cascadeAttach)
            {
                // Determine if field is "second-class" (no identity) - if has "embedded" info or serialised
                boolean sco = (mmd.getEmbeddedMetaData() != null || mmd.isSerialized() || mmd.isEmbedded());

                if (copy)
                {
                    // Attach copy of the PC
                    value = ec.attachObjectCopy(attachedOP, value, sco);
                    attachedOP.replaceFieldMakeDirty(fieldNumber, value);
                }
                else
                {
                    // Attach PC in-situ
                    ec.attachObject(attachedOP, value, sco);
                }

                // Make sure the field is marked as dirty
                if (dirtyFields[fieldNumber] || !persistent)
                {
                    attachedOP.makeDirty(fieldNumber);
                }
                else if (sco && value != null && api.isDirty(value))
                {
                    attachedOP.makeDirty(fieldNumber);
                }
            }
            else if (dirtyFields[fieldNumber] || !persistent)
            {
                attachedOP.makeDirty(fieldNumber);
            }
        }
        else
        {
            attachedOP.replaceField(fieldNumber, value);
            if (dirtyFields[fieldNumber] || !persistent)
            {
                attachedOP.makeDirty(fieldNumber);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeBooleanField(int, boolean)
     */
    public void storeBooleanField(int fieldNumber, boolean value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeBooleanField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeByteField(int, byte)
     */
    public void storeByteField(int fieldNumber, byte value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeByteField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeCharField(int, char)
     */
    public void storeCharField(int fieldNumber, char value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeCharField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeDoubleField(int, double)
     */
    public void storeDoubleField(int fieldNumber, double value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeDoubleField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeFloatField(int, float)
     */
    public void storeFloatField(int fieldNumber, float value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeFloatField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeIntField(int, int)
     */
    public void storeIntField(int fieldNumber, int value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeIntField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeLongField(int, long)
     */
    public void storeLongField(int fieldNumber, long value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeLongField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeShortField(int, short)
     */
    public void storeShortField(int fieldNumber, short value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeShortField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }

    /*
     * (non-Javadoc)
     * @see FieldConsumer#storeStringField(int, java.lang.String)
     */
    public void storeStringField(int fieldNumber, String value)
    {
        SingleValueFieldManager sfv = new SingleValueFieldManager();
        sfv.storeStringField(fieldNumber, value);
        attachedOP.replaceFields(new int[]{fieldNumber}, sfv);
        if (dirtyFields[fieldNumber] || !persistent)
        {
            attachedOP.makeDirty(fieldNumber);
        }
    }
}