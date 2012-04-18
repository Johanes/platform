/**********************************************************************
Copyright (c) 2002 Kelly Grizzle and others. All rights reserved.
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
2003 Andy Jefferson - commented
2006 Andy Jefferson - rewritten to be independent of JDO
    ...
**********************************************************************/
package org.datanucleus;

import java.util.Map;

import javax.transaction.Synchronization;

import org.datanucleus.exceptions.NucleusUserException;

/**
 * Representation of a transaction within DataNucleus. This interface is not
 * user application visible.
 * 
 * Handling of transactions in DataNucleus is split in 4 layers:
 * <li>
 * <ul>API - The User Visible Transaction API</ul>
 * <ul>ObjectManager Transaction - The Transaction assigned to a ObjectManager</ul>
 * <ul>X/Open/JTA - The Transaction Manager associated to the underlying datastore transaction</ul>
 * <ul>Resource - The Transaction handled by the datastore</ul>
 * </li>
 *
 * In the the API layer, there are interfaces provided to the user application, as such:
 * <li>
 * <ul>{@link javax.jdo.Transaction} - the JDO API interface</ul>
 * <ul>javax.persistence.EntityTransaction - the JPA API interface</ul>
 * <ul>{@link javax.transaction.UserTransaction} - the JTA API interface</ul>
 * <ul>{@link org.datanucleus.UserTransaction} - DataNucleus API proprietary API</ul>
 * </li>
 *
 * In the ObjectManager layer, the {@link org.datanucleus.Transaction} interface defines the contract
 * for handling transactions for the ObjectManager.
 * 
 * In the X/Open/JTA layer the handling of XA resources is done. It means, XAResources are
 * obtained and enlisted to a TransactionManager. The TransactionManager will commit or rollback the resources
 * at the end of the transactions. There are two kinds of TransactionManager: DataNucleus and JTA. A
 * JTA TransactionManager is external to DataNucleus, while the DataNucleus TransactionManager is implemented
 * by DataNucleus as {@link org.datanucleus.transaction}. The DataNucleus TransactionManager is used when the DataSource used
 * to obtain connections to the underlying database is not enlisted in an external JTA TransactionManager.
 * The JTA TransactionManager is usually found when running in J2EE application servers, however
 * nowadays there are many JTA containers that can be used in J2SE.
 * 
 * The scenarios where a JTA TransactionManager is used is:
 * When an JTA TransactionManager exists, and the connections to the underlying databases
 * are acquired via transactional DataSources. That means, when you ask a connection to the DataSource,
 * it will automatically enlist it in a JTA TransactionManager.
 *   
 * The Resource layer is handled by the datastore. For example, with RDBMS databases,
 * the javax.sql.Connection is the API used to demarcate the database transactions. In The RBDMS database,
 * the resource layer, it is handling the database transaction.
 */ 
public interface Transaction
{
    /** Option to use when wanting to set the transaction isolation level. */
    public static final String TRANSACTION_ISOLATION_OPTION = "transaction.isolation";

    /**
     * Begin a transaction.
     * The type of transaction (datastore/optimistic) is determined by the setting of the Optimistic flag.
     * @throws NucleusUserException if transactions are managed by a container
     *     in the managed environment, or if the transaction is already active.
     */
    void begin();

    /**
     * Commit the current transaction. The commit will trigger flushing the transaction, will
     * invoke the preCommit, commit the resources and invoke postCommit listeners. 
     * 
     * If during flush or preCommit phases a NucleusUserException is raised, then the transaction will not
     * complete and the transaction remains active. The NucleusUserException is cascaded to the caller.
     * 
     * @throws NucleusUserException if transactions are managed by a container
     *     in the managed environment, or if the transaction is not active.
     */
    void commit();

    /**
     * Rollback the current transaction. The commit will trigger flushing the transaction, will
     * invoke the preRollback, rollback the resources and invoke postRollback listeners. 
     * 
     * If during flush or preRollback phases a NucleusUserException is raised, then the transaction will not
     * complete and the transaction remains active. The NucleusUserException is cascaded to the caller.
     * 
     * @throws NucleusUserException if transactions are managed by a container
     *     in the managed environment, or if the transaction is not active.
     */
    void rollback();

    /**
     * Returns whether there is a transaction currently active.
     * @return Whether the transaction is active.
     */
    boolean isActive();

    /**
     * Method to allow the transaction to flush any resources.
     */
    void flush();

    /**
     * Method to allow the transaction to flush any resources.
     */
    void end();
    
    /**
     * Returns the rollback-only status of the transaction. 
     * When begun, the rollback-only status is false. Either the
     * application or the JDO implementation may set this flag
     * using setRollbackOnly.
     * @return Whether the transaction has been marked for rollback.
     */
    boolean getRollbackOnly();

    /**
     * Sets the rollback-only status of the transaction to <code>true</code>.
     * After this flag is set to <code>true</code>, the transaction
     * can no longer be committed.
     * @throws NucleusUserException if the flag is true and an attempt is made
     *     to commit the txn
     */
    void setRollbackOnly();

    /**
     * If <code>true</code>, allow persistent instances to be read without
     * a transaction active.
     * If an implementation does not support this option, a
     * @param nontransactionalRead Whether to have non-tx reads
     * @throws NucleusUserException if not supported
     */
    void setNontransactionalRead(boolean nontransactionalRead);


    /**
     * If <code>true</code>, allows persistent instances to be read without
     * a transaction active.
     * @return Whether we are allowing non-tx reads
     */
    boolean getNontransactionalRead();

    /**
     * If <code>true</code>, allow persistent instances to be written without
     * a transaction active.
     * @param nontransactionalWrite Whether requiring non-tx writes
     * @throws NucleusUserException if not supported
     */
    void setNontransactionalWrite(boolean nontransactionalWrite);

    /**
     * If <code>true</code>, allows persistent instances to be written without
     * a transaction active.
     * @return Whether we are allowing non-tx writes
     */
    boolean getNontransactionalWrite();

    /**
     * If <code>true</code>, at commit instances retain their values and the
     * instances transition to persistent-nontransactional.
     * @param retainValues the value of the retainValues property
     * @throws NucleusUserException if not supported
     */
    void setRetainValues(boolean retainValues);

    /**
     * If <code>true</code>, at commit time instances retain their field values.
     * @return the value of the retainValues property
     */
    boolean getRetainValues();

    /**
     * If <code>true</code>, at rollback, fields of newly persistent instances
     * are restored to their values as of the beginning of the transaction, and 
     * the instances revert to transient. Additionally, fields of modified
     * instances of primitive types and immutable reference types
     * are restored to their values as of the beginning of the
     * transaction.
     * <P>If <code>false</code>, at rollback, the values of fields of
     * newly persistent instances are unchanged and the instances revert to
     * transient.  Additionally, dirty instances transition to hollow.
     * @param restoreValues the value of the restoreValues property
     * @throws NucleusUserException if not supported
     */
    void setRestoreValues(boolean restoreValues);

    /**
     * Return the current value of the restoreValues property.
     * @return the value of the restoreValues property
     */
    boolean getRestoreValues();

    /**
     * Optimistic transactions do not hold data store locks until commit time.
     * @param optimistic the value of the Optimistic flag.
     * @throws NucleusUserException if not supported
     */
    void setOptimistic(boolean optimistic);

    /**
     * Optimistic transactions do not hold data store locks until commit time.
     * @return the value of the Optimistic property.
     */
    boolean getOptimistic();

    /**
     * Mutator for whether to serialize (lock) any read objects in this transaction.
     * @param serializeRead Whether to serialise (lock) any read objects
     */
    void setSerializeRead(Boolean serializeRead);

    /**
     * Accessor for the setting for whether to serialize read objects (lock them).
     * @return the value of the serializeRead property
     */
    Boolean getSerializeRead();

    /**
     * The user can specify a <code>Synchronization</code> instance to be
     * notified on transaction completions.  The <code>beforeCompletion</code>
     * method is called prior to flushing instances to the data store.
     *
     * <P>The <code>afterCompletion</code> method is called after performing
     * state transitions of persistent and transactional instances, following
     * the data store commit or rollback operation.
     * <P>Only one <code>Synchronization</code> instance can be registered with
     * the  <code>Transaction</code>. If the application requires more than one
     * instance to receive synchronization callbacks, then the single
     * application instance is responsible for managing them, and forwarding
     * callbacks to them.
     * @param sync the <code>Synchronization</code> instance to be notified;
     * <code>null</code> for none
     */
    void setSynchronization(Synchronization sync);

    /** The user-specified <code>Synchronization</code> instance for this
     * <code>Transaction</code> instance.
     * @return the user-specified <code>Synchronization</code> instance.
     */
    Synchronization getSynchronization();

    /**
     * Checks whether a transaction is committing.
     * @return Whether the transaction is committing
     */
    boolean isCommitting();

    /**
     * Adds a transaction listener. After commit or rollback, listeners are cleared
     * @param listener
     */
    void addTransactionEventListener(TransactionEventListener listener);

    /**
     * Removes the specified listener.
     * @param listener Listener
     */
    void removeTransactionEventListener(TransactionEventListener listener);

    /**
     * Listeners that are never cleared, and invoked for all transactions
     * @param listener
     */
    void bindTransactionEventListener(TransactionEventListener listener);

    /**
     * Obtain all settings for this Transaction
     * @return a map with settings
     */
    Map<String, Object> getOptions();

    /**
     * Convenience accessor providing a simple true/false for locking read objects.
     * @return Whether to lock read objects
     */
    boolean lockReadObjects();

    void setOption(String option, int value);
    
    void setOption(String option, boolean value);

    void setOption(String option, String value);
}