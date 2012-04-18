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
2007 Xuan Baldauf - Contrib of notifyMainMemoryCopyIsInvalid(), findObject() (needed by DB4O plugin).
    ...
**********************************************************************/
package org.datanucleus.store;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.NucleusContext;
import org.datanucleus.Transaction;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.exceptions.ClassNotResolvedException;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.identity.OID;
import org.datanucleus.identity.SCOID;
import org.datanucleus.management.ManagementManager;
import org.datanucleus.management.ManagementServer;
import org.datanucleus.management.runtime.StoreManagerRuntime;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ClassMetaData;
import org.datanucleus.metadata.ClassPersistenceModifier;
import org.datanucleus.metadata.ExtensionMetaData;
import org.datanucleus.metadata.IdentityMetaData;
import org.datanucleus.metadata.IdentityStrategy;
import org.datanucleus.metadata.IdentityType;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.metadata.SequenceMetaData;
import org.datanucleus.metadata.TableGeneratorMetaData;
import org.datanucleus.plugin.ConfigurationElement;
import org.datanucleus.properties.PropertyStore;
import org.datanucleus.store.autostart.AutoStartMechanism;
import org.datanucleus.store.autostart.AutoStartMechanism.Mode;
import org.datanucleus.store.connection.ConnectionFactory;
import org.datanucleus.store.connection.ConnectionManager;
import org.datanucleus.store.connection.ConnectionManagerImpl;
import org.datanucleus.store.connection.ManagedConnection;
import org.datanucleus.store.encryption.ConnectionEncryptionProvider;
import org.datanucleus.store.exceptions.DatastoreInitialisationException;
import org.datanucleus.store.exceptions.DatastoreReadOnlyException;
import org.datanucleus.store.exceptions.NoExtentException;
import org.datanucleus.store.query.QueryManager;
import org.datanucleus.store.schema.StoreSchemaHandler;
import org.datanucleus.store.valuegenerator.AbstractDatastoreGenerator;
import org.datanucleus.store.valuegenerator.ValueGenerationConnectionProvider;
import org.datanucleus.store.valuegenerator.ValueGenerationManager;
import org.datanucleus.store.valuegenerator.ValueGenerator;
import org.datanucleus.util.ClassUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;
import org.datanucleus.util.TypeConversionHelper;

/**
 * An abstract representation of a Store Manager.
 * Manages the persistence of objects to the store.
 * Will be implemented for the type of datastore (RDBMS, ODBMS, etc) in question. 
 * The store manager's responsibilities include:
 * <ul>
 * <li>Creating and/or validating datastore tables according to the persistent classes being 
 * accessed by the application.</li>
 * <li>Serving as the primary intermediary between StateManagers and the database.</li>
 * <li>Serving as the base Extent and Query factory.</li>
 * </ul>
 * <p>
 * A store manager's knowledge of its contents is typically not complete. It knows about
 * the classes that it has encountered in its lifetime. The PersistenceManager can make the
 * StoreManager aware of a class, and can check if the StoreManager knows about a particular class.
 * The Auto-Start mechanism provides a way of inheriting knowledge from the last time the store was used.
 */
public abstract class AbstractStoreManager extends PropertyStore implements StoreManager
{
    /** Localiser for messages. */
    protected static final Localiser LOCALISER = Localiser.getInstance(
        "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /** Key for this StoreManager e.g "rdbms", "db4o" */
    protected final String storeManagerKey;

    /** Whether this datastore is read only. */
    protected final boolean readOnlyDatastore;

    /** Whether this datastore is fixed (no mods to table structure allowed). */
    protected final boolean fixedDatastore;

    /** Whether to auto create any tables. */
    protected final boolean autoCreateTables;

    /** Whether to auto create any columns that are missing. */
    protected final boolean autoCreateColumns;

    /** Whether to auto create any constraints */
    protected final boolean autoCreateConstraints;

    /** Whether to warn only when any errors occur on auto-create. */
    protected final boolean autoCreateWarnOnError;

    /** Whether to validate any tables */
    protected final boolean validateTables;

    /** Whether to validate any columns */
    protected final boolean validateColumns;

    /** Whether to validate any constraints */
    protected final boolean validateConstraints;

    /** Auto-Start mechanism to use. */
    protected AutoStartMechanism starter = null;

    /** Whether the AutoStart mechanism is initialised */
    protected boolean starterInitialised = false;

    /** Nucleus Context. */
    protected final NucleusContext nucleusContext;

    /** Manager for value generation. Lazy initialised, so use getValueGenerationManager() to access. */
    private ValueGenerationManager valueGenerationMgr;

    /** StoreManager Runtime. Used when providing management of services. */
    protected StoreManagerRuntime storeManagerRuntime;

    /** Manager for the data definition in the datastore. */
    protected StoreDataManager storeDataMgr = new StoreDataManager();

    /** Name of the AutoStart mechanism. */
    protected String autoStartMechanism = null;

    /** Persistence handler. */
    protected StorePersistenceHandler persistenceHandler = null;

    /** Query Manager. Lazy initialised, so use getQueryManager() to access. */
    private QueryManager queryMgr = null;

    /** Schema handler. */
    protected StoreSchemaHandler schemaHandler = null;

    /** ConnectionManager **/
    protected ConnectionManager connectionMgr;

    /** Name of transactional connection factory. */
    protected String txConnectionFactoryName;

    /** Name of non-transactional connection factory (null if not present). */
    protected String nontxConnectionFactoryName;

    /**
     * Constructor for a new StoreManager. Stores the basic information required for the datastore management.
     * @param key Key for this StoreManager
     * @param clr the ClassLoaderResolver
     * @param nucleusContext The corresponding nucleus context.
     * @param props Any properties controlling this datastore
     */
    protected AbstractStoreManager(String key, ClassLoaderResolver clr, NucleusContext nucleusContext,
            Map<String, Object> props)
    {
        this.storeManagerKey = key;
        this.nucleusContext = nucleusContext;
        if (props != null)
        {
            // Store the properties for this datastore
            Iterator<Map.Entry<String, Object>> propIter = props.entrySet().iterator();
            while (propIter.hasNext())
            {
                Map.Entry<String, Object> entry = propIter.next();
                setPropertyInternal(entry.getKey(), entry.getValue());
            }
        }

        // Set up schema controls
        this.readOnlyDatastore = getBooleanProperty("datanucleus.readOnlyDatastore");
        this.fixedDatastore = getBooleanProperty("datanucleus.fixedDatastore");
        if (readOnlyDatastore || fixedDatastore)
        {
            autoCreateTables = false;
            autoCreateColumns = false;
            autoCreateConstraints = false;
        }
        else
        {
            boolean autoCreateSchema = getBooleanProperty("datanucleus.autoCreateSchema");
            if (autoCreateSchema)
            {
                autoCreateTables = true;
                autoCreateColumns = true;
                autoCreateConstraints = true;
            }
            else
            {
                autoCreateTables = getBooleanProperty("datanucleus.autoCreateTables");
                autoCreateColumns = getBooleanProperty("datanucleus.autoCreateColumns");
                autoCreateConstraints = getBooleanProperty("datanucleus.autoCreateConstraints");
            }
        }
        autoCreateWarnOnError = getBooleanProperty("datanucleus.autoCreateWarnOnError");

        boolean validateSchema = getBooleanProperty("datanucleus.validateSchema");
        if (validateSchema)
        {
            validateTables = true;
            validateColumns = true;
            validateConstraints = true;
        }
        else
        {
            validateTables = getBooleanProperty("datanucleus.validateTables");
            if (!validateTables)
            {
                validateColumns = false;
            }
            else
            {
                validateColumns = getBooleanProperty("datanucleus.validateColumns");
            }
            validateConstraints = getBooleanProperty("datanucleus.validateConstraints");
        }

        autoStartMechanism = getStringProperty("datanucleus.autoStartMechanism");

        // Register MBean if being managed
        if (nucleusContext.getJMXManager() != null)
        {
            ManagementManager mgmtMgr = nucleusContext.getJMXManager();
            ManagementServer mgntServer = nucleusContext.getJMXManager().getManagementServer();
            this.storeManagerRuntime = new StoreManagerRuntime();
            String mbeanName = mgmtMgr.getDomainName() + ":InstanceName=" + mgmtMgr.getInstanceName() +
                ",Type=" + ClassUtils.getClassNameForClass(storeManagerRuntime.getClass()) +
                ",Name=StoreManagerRuntime";
            mgntServer.registerMBean(this.storeManagerRuntime, mbeanName);
        }

        // Set up connection handling
        registerConnectionMgr();
        registerConnectionFactory();
        nucleusContext.addObjectManagerListener(new ExecutionContext.LifecycleListener()
        {
            public void preClose(ExecutionContext ec)
            {
                // Close all connections for this ExecutionContext whether tx or non-tx when ObjectManager closes
                ConnectionFactory connFactory = connectionMgr.lookupConnectionFactory(txConnectionFactoryName);
                connectionMgr.closeAllConnections(connFactory, ec);
                connFactory = connectionMgr.lookupConnectionFactory(nontxConnectionFactoryName);
                connectionMgr.closeAllConnections(connFactory, ec);
            }
        });
    }

    /**
     * Register the default ConnectionManager implementation
     */
    protected void registerConnectionMgr()
    {
        this.connectionMgr = new ConnectionManagerImpl(nucleusContext);
    }
    
    /**
     * Register the Connection Factory defined in plugins
     */
    protected void registerConnectionFactory()
    {
        // Factory for connections - transactional
        ConfigurationElement cfElem = nucleusContext.getPluginManager().getConfigurationElementForExtension(
            "org.datanucleus.store_connectionfactory",
            new String[] {"datastore", "transactional"}, new String[] {storeManagerKey, "true"});
        if (cfElem != null)
        {
            txConnectionFactoryName = cfElem.getAttribute("name");
            try
            {
                ConnectionFactory cf = (ConnectionFactory)nucleusContext.getPluginManager().createExecutableExtension(
                    "org.datanucleus.store_connectionfactory",
                    new String[] {"datastore", "transactional"},
                    new String[] {storeManagerKey, "true"}, "class-name",
                    new Class[] {StoreManager.class, String.class},
                    new Object[] {this, "tx"});
                connectionMgr.registerConnectionFactory(txConnectionFactoryName, cf);
                if (NucleusLogger.CONNECTION.isDebugEnabled())
                {
                    NucleusLogger.CONNECTION.debug(LOCALISER.msg("032018", txConnectionFactoryName));
                }
            }
            catch (Exception e)
            {
                throw new NucleusException("Error creating transactional connection factory", e).setFatal();
            }
        }
        else
        {
            throw new NucleusException("Error creating transactional connection factory. No connection factory plugin defined");
        }

        // Factory for connections - nontransactional
        cfElem = nucleusContext.getPluginManager().getConfigurationElementForExtension(
            "org.datanucleus.store_connectionfactory",
            new String[] {"datastore", "transactional"}, new String[] {storeManagerKey, "false"});
        if (cfElem != null)
        {
            nontxConnectionFactoryName = cfElem.getAttribute("name");
            try
            {
                ConnectionFactory cf = (ConnectionFactory)nucleusContext.getPluginManager().createExecutableExtension(
                    "org.datanucleus.store_connectionfactory",
                    new String[] {"datastore", "transactional"},
                    new String[] {storeManagerKey, "false"}, "class-name",
                    new Class[] {StoreManager.class, String.class},
                    new Object[] {this, "nontx"});
                if (NucleusLogger.CONNECTION.isDebugEnabled())
                {
                    NucleusLogger.CONNECTION.debug(LOCALISER.msg("032019", nontxConnectionFactoryName));
                }
                connectionMgr.registerConnectionFactory(nontxConnectionFactoryName, cf);
            }
            catch (Exception e)
            {
                throw new NucleusException("Error creating nontransactional connection factory", e).setFatal();
            }
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#close()
     */
    public synchronized void close()
    {
        if (txConnectionFactoryName != null)
        {
            ConnectionFactory cf = connectionMgr.lookupConnectionFactory(txConnectionFactoryName);
            if (cf != null)
            {
                cf.close();
            }
        }
        if (nontxConnectionFactoryName != null)
        {
            ConnectionFactory cf = connectionMgr.lookupConnectionFactory(nontxConnectionFactoryName);
            if (cf != null)
            {
                cf.close();
            }
        }

        if (valueGenerationMgr != null)
        {
            valueGenerationMgr.clear();
        }

        storeDataMgr.clear();

        starterInitialised = false;
        starter = null;

        if (persistenceHandler != null)
        {
            persistenceHandler.close();
            persistenceHandler = null;
        }

        if (queryMgr != null)
        {
            queryMgr.close();
            queryMgr = null;
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getConnectionManager()
     */
    public ConnectionManager getConnectionManager()
    {
        return connectionMgr;
    }

    /**
     * Accessor for a connection for the specified ObjectManager.<p>
     * If there is an active transaction, a connection from the transactional
     * connection factory will be returned. If there is no active transaction,
     * a connection from the nontransactional connection factory will be returned.
     * @param ec execution context
     * @return The Connection
     * @throws NucleusException Thrown if an error occurs getting the connection
     */
    public ManagedConnection getConnection(ExecutionContext ec)
    {
        return getConnection(ec, null);
    }

    /**
     * Accessor for a connection for the specified ObjectManager.<p>
     * If there is an active transaction, a connection from the transactional
     * connection factory will be returned. If there is no active transaction,
     * a connection from the nontransactional connection factory will be returned.
     * @param ec execution context
     * @return The Connection
     * @throws NucleusException Thrown if an error occurs getting the connection
     */
    public ManagedConnection getConnection(ExecutionContext ec, Map options)
    {
        ConnectionFactory connFactory;
        if (ec.getTransaction().isActive())
        {
            connFactory = connectionMgr.lookupConnectionFactory(txConnectionFactoryName);
        }
        else
        {
            if (nontxConnectionFactoryName != null)
            {
                connFactory = connectionMgr.lookupConnectionFactory(nontxConnectionFactoryName);
            }
            else
            {
                // Some datastores don't define non-tx handling so just fallback to the primary factory
                connFactory = connectionMgr.lookupConnectionFactory(txConnectionFactoryName);
            }
        }
        return connFactory.getConnection(ec, ec.getTransaction(), options);
    }

    /**
     * Utility to return a non-transactional Connection not tied to any ExecutionContext.
     * This returns a connection from a secondary DataSource (e.g. javax.jdo.option.connectionFactory2Name),
     * if it is provided.
     * @param isolation_level The transaction isolation scheme to use e.g Connection.TRANSACTION_NONE
     *     Pass in -1 if just want the default
     * @return The Connection to the datastore
     * @throws NucleusException if an error occurs getting the connection
     */
    public ManagedConnection getConnection(int isolation_level)
    {
        ConnectionFactory connFactory = null;
        if (nontxConnectionFactoryName != null)
        {
            connFactory = connectionMgr.lookupConnectionFactory(nontxConnectionFactoryName);
        }
        else
        {
            // Some datastores don't define non-tx handling so just fallback to the primary factory
            connFactory = connectionMgr.lookupConnectionFactory(txConnectionFactoryName);
        }

        Map options = null;
        if (isolation_level >= 0)
        {
            options = new HashMap();
            options.put(Transaction.TRANSACTION_ISOLATION_OPTION, Integer.valueOf(isolation_level));
        }
        return connFactory.getConnection(null, null, options);
    }

    /**
     * Convenience accessor for the driver name to use for the connection (if applicable for this datastore).
     * @return Driver name for the connection
     */
    public String getConnectionDriverName()
    {
        return getStringProperty("datanucleus.ConnectionDriverName");
    }

    /**
     * Convenience accessor for the URL for the connection
     * @return Connection URL
     */
    public String getConnectionURL()
    {
        return getStringProperty("datanucleus.ConnectionURL");
    }

    /**
     * Convenience accessor for the username to use for the connection
     * @return Username
     */
    public String getConnectionUserName()
    {
        return getStringProperty("datanucleus.ConnectionUserName");
    }

    /**
     * Convenience accessor for the password to use for the connection.
     * Will perform decryption if the persistence property "datanucleus.ConnectionPasswordDecrypter" has
     * also been specified.
     * @return Password
     */
    public String getConnectionPassword()
    {
        String password = getStringProperty("datanucleus.ConnectionPassword");
        if (password != null)
        {
            String decrypterName = getStringProperty("datanucleus.ConnectionPasswordDecrypter");
            if (decrypterName != null)
            {
                // Decrypt the password using the provided class
                ClassLoaderResolver clr = nucleusContext.getClassLoaderResolver(null);
                try
                {
                    Class decrypterCls = clr.classForName(decrypterName);
                    ConnectionEncryptionProvider decrypter = (ConnectionEncryptionProvider) decrypterCls.newInstance();
                    password = decrypter.decrypt(password);
                }
                catch (Exception e)
                {
                    NucleusLogger.DATASTORE.warn("Error invoking decrypter class " + decrypterName, e);
                }
            }
        }
        return password;
    }

    /**
     * Convenience accessor for the factory for the connection (transactional).
     * @return Connection Factory (transactional)
     */
    public Object getConnectionFactory()
    {
        return getProperty("datanucleus.ConnectionFactory");
    }

    /**
     * Convenience accessor for the factory name for the connection (transactional).
     * @return Connection Factory name (transactional)
     */
    public String getConnectionFactoryName()
    {
        return getStringProperty("datanucleus.ConnectionFactoryName");
    }

    /**
     * Convenience accessor for the factory for the connection (non-transactional).
     * @return Connection Factory (non-transactional)
     */
    public Object getConnectionFactory2()
    {
        return getProperty("datanucleus.ConnectionFactory2");
    }

    /**
     * Convenience accessor for the factory name for the connection (non-transactional).
     * @return Connection Factory name (non-transactional)
     */
    public String getConnectionFactory2Name()
    {
        return getStringProperty("datanucleus.ConnectionFactory2Name");
    }

    public boolean isAutoCreateTables()
    {
        return autoCreateTables;
    }

    public boolean isAutoCreateColumns()
    {
        return autoCreateColumns;
    }

    public boolean isAutoCreateConstraints()
    {
        return autoCreateConstraints;
    }

    public boolean isValidateTables()
    {
        return validateTables;
    }

    public boolean isValidateColumns()
    {
        return validateColumns;
    }

    public boolean isValidateConstraints()
    {
        return validateConstraints;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getRuntimeManager()
     */
    public StoreManagerRuntime getRuntimeManager()
    {
        return storeManagerRuntime;
    }

	/* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getPersistenceHandler()
     */
    public StorePersistenceHandler getPersistenceHandler()
    {
        return persistenceHandler;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getQueryManager()
     */
    public QueryManager getQueryManager()
    {
        if (queryMgr == null)
        {
            // Initialise support for queries
            queryMgr = new QueryManager(nucleusContext, this);
        }
        return queryMgr;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getMetaDataHandler()
     */
    public StoreSchemaHandler getSchemaHandler()
    {
        return schemaHandler;
    }

    /**
     * Method to return a datastore sequence for this datastore matching the passed sequence MetaData.
     * @param ec execution context
     * @param seqmd SequenceMetaData
     * @return The Sequence
     */
    public NucleusSequence getNucleusSequence(ExecutionContext ec, SequenceMetaData seqmd)
    {
        return new NucleusSequenceImpl(ec, this, seqmd);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getNucleusConnection(org.datanucleus.ObjectManager)
     */
    public NucleusConnection getNucleusConnection(ExecutionContext ec)
    {
        ConnectionFactory cf = connectionMgr.lookupConnectionFactory(txConnectionFactoryName);

        final ManagedConnection mc;
        final boolean enlisted;
        if (!ec.getTransaction().isActive())
        {
            // no active transaction so don't enlist
            enlisted = false;
        }
        else
        {
            enlisted = true;
        }
        mc = cf.getConnection(enlisted ? ec : null, enlisted ? ec.getTransaction() : null, null); // Will throw exception if already locked

        // Lock the connection now that it is in use by the user
        mc.lock();

        Runnable closeRunnable = new Runnable()
        {
            public void run()
            {
                // Unlock the connection now that the user has finished with it
                mc.unlock();
                if (!enlisted)
                {
                    // Close the (unenlisted) connection (committing its statements)
                    mc.close();
                }
            }
        };
        return new NucleusConnectionImpl(mc.getConnection(), closeRunnable);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getValueGenerationManager()
     */
    public ValueGenerationManager getValueGenerationManager()
    {
        if (valueGenerationMgr == null)
        {
            this.valueGenerationMgr = new ValueGenerationManager();
        }
        return valueGenerationMgr;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getApiAdapter()
     */
    public ApiAdapter getApiAdapter()
    {
        return nucleusContext.getApiAdapter();
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getStoreManagerKey()
     */
    public String getStoreManagerKey()
    {
        return storeManagerKey;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getQueryCacheKey()
     */
    public String getQueryCacheKey()
    {
        // Fall back to the same as the key for the store manager
        return getStoreManagerKey();
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getNucleusContext()
     */   
    public NucleusContext getNucleusContext()
    {
        return nucleusContext;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getMetaDataManager()
     */   
    public MetaDataManager getMetaDataManager()
    {
        return nucleusContext.getMetaDataManager();
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getDatastoreDate()
     */
    public Date getDatastoreDate()
    {
        throw new UnsupportedOperationException();
    }

    // -------------------------------- Management of Classes --------------------------------

    /**
     * Method to register some data with the store.
     * This will also register the data with the starter process.
     * @param data The StoreData to add
     */
    protected void registerStoreData(StoreData data)
    {
        storeDataMgr.registerStoreData(data);

        // Keep the AutoStarter in step with our managed classes/fields
        if (starter != null && starterInitialised)
        {
            starter.addClass(data);
        }
    }

    /**
     * Method to deregister all existing store data so that we are managing nothing.
     */
    protected void deregisterAllStoreData()
    {
        storeDataMgr.clear();
        starterInitialised = false;

        // Keep the AutoStarter in step with our managed classes/fields
        clearAutoStarter();
    }

    /**
     * Convenience method to log the configuration of this store manager.
     */
    protected void logConfiguration()
    {
        if (NucleusLogger.DATASTORE.isDebugEnabled())
        {
            NucleusLogger.DATASTORE.debug("======================= Datastore =========================");
            NucleusLogger.DATASTORE.debug("StoreManager : \"" + storeManagerKey + "\" (" + getClass().getName() + ")");

            if (autoStartMechanism != null)
            {
                String classNames = getStringProperty("datanucleus.autoStartClassNames");
                NucleusLogger.DATASTORE.debug("AutoStart : mechanism=" + autoStartMechanism + 
                    ", mode=" + getStringProperty("datanucleus.autoStartMechanismMode") +
                    ((classNames != null) ? (", classes=" + classNames) : ""));
            }

            NucleusLogger.DATASTORE.debug("Datastore : " +
                (readOnlyDatastore ? "read-only" : "read-write") + (fixedDatastore ? ", fixed" : "") +
                (getBooleanProperty("datanucleus.SerializeRead") ? ", useLocking" : ""));

            // Schema : Auto-Create/Validate
            StringBuffer autoCreateOptions = null;
            if (autoCreateTables || autoCreateColumns || autoCreateConstraints)
            {
                autoCreateOptions = new StringBuffer();
                boolean first = true;
                if (autoCreateTables)
                {
                    if (!first)
                    {
                        autoCreateOptions.append(",");
                    }
                    autoCreateOptions.append("Tables");
                    first = false;
                }
                if (autoCreateColumns)
                {
                    if (!first)
                    {
                        autoCreateOptions.append(",");
                    }
                    autoCreateOptions.append("Columns");
                    first = false;
                }
                if (autoCreateConstraints)
                {
                    if (!first)
                    {
                        autoCreateOptions.append(",");
                    }
                    autoCreateOptions.append("Constraints");
                    first = false;
                }
            }
            StringBuffer validateOptions = null;
            if (validateTables || validateColumns || validateConstraints)
            {
                validateOptions = new StringBuffer();
                boolean first = true;
                if (validateTables)
                {
                    validateOptions.append("Tables");
                    first = false;
                }
                if (validateColumns)
                {
                    if (!first)
                    {
                        validateOptions.append(",");
                    }
                    validateOptions.append("Columns");
                    first = false;
                }
                if (validateConstraints)
                {
                    if (!first)
                    {
                        validateOptions.append(",");
                    }
                    validateOptions.append("Constraints");
                    first = false;
                }
            }
            NucleusLogger.DATASTORE.debug("Schema Control : " +
                "AutoCreate(" + (autoCreateOptions != null ? autoCreateOptions.toString() : "None") + ")" +
                ", Validate(" + (validateOptions != null ? validateOptions.toString() : "None") + ")");

            String[] queryLanguages = nucleusContext.getPluginManager().getAttributeValuesForExtension(
                "org.datanucleus.store_query_query", "datastore", storeManagerKey, "name");
            NucleusLogger.DATASTORE.debug("Query Languages : " + 
                (queryLanguages != null ? StringUtils.objectArrayToString(queryLanguages) : "none"));
            NucleusLogger.DATASTORE.debug("Queries : Timeout=" +
                getIntProperty("datanucleus.datastoreReadTimeout"));

            NucleusLogger.DATASTORE.debug("===========================================================");
        }
    }

    /**
     * Method to output the information about the StoreManager.
     * Supports the category "DATASTORE".
     */
    public void printInformation(String category, PrintStream ps)
    throws Exception
    {
        if (category.equalsIgnoreCase("DATASTORE"))
        {
            ps.println(LOCALISER.msg("032020", storeManagerKey, getConnectionURL(),
                (readOnlyDatastore ? "read-only" : "read-write"), (fixedDatastore ? ", fixed" : "")));
        }
    }

    // -------------------------------- Auto Starter ------------------------------------

    /**
     * Method to initialise the auto-start mechanism, loading up the classes
     * from its store into memory so that we start from where we got to last time.
     * Utilises the "autoStartMechanism" field that should have been set before now.
     * @param clr The ClassLoaderResolver
     * @throws DatastoreInitialisationException
     */
    protected void initialiseAutoStart(ClassLoaderResolver clr)
    throws DatastoreInitialisationException
    {
        if (starterInitialised)
        {
            return;
        }
        if (autoStartMechanism == null)
        {
            // No starter needed
            autoStartMechanism = "None";
            starterInitialised = true;
            return;
        }

        String autoStarterClassName = 
            getNucleusContext().getPluginManager().getAttributeValueForExtension("org.datanucleus.autostart", 
                "name", autoStartMechanism, "class-name");
        if (autoStarterClassName != null)
        {
            String mode = getStringProperty("datanucleus.autoStartMechanismMode");
            Class[] argsClass = new Class[] {org.datanucleus.store.StoreManager.class, org.datanucleus.ClassLoaderResolver.class};
            Object[] args = new Object[] {this, clr};
            try
            {
                starter = (AutoStartMechanism) getNucleusContext().getPluginManager().createExecutableExtension(
                    "org.datanucleus.autostart", "name", autoStartMechanism, "class-name", argsClass, args);
                if (mode.equalsIgnoreCase("None"))
                {
                    starter.setMode(Mode.NONE);
                }
                else if (mode.equalsIgnoreCase("Checked"))
                {
                    starter.setMode(Mode.CHECKED);
                }
                else if (mode.equalsIgnoreCase("Quiet"))
                {
                    starter.setMode(Mode.QUIET);
                }
                else if (mode.equalsIgnoreCase("Ignored"))
                {
                    starter.setMode(Mode.IGNORED);
                }
            }
            catch (Exception e)
            {
                NucleusLogger.PERSISTENCE.error(StringUtils.getStringFromStackTrace(e));
            }
        }
        if (starter == null)
        {
            starterInitialised = true;
            return;
        }

        boolean illegalState = false;
        try
        {
            if (!starter.isOpen())
            {
                starter.open();
            }
            Collection existingData = starter.getAllClassData();
            if (existingData != null && existingData.size() > 0)
            {
                List classesNeedingAdding = new ArrayList();
                Iterator existingDataIter = existingData.iterator();
                while (existingDataIter.hasNext())
                {
                    StoreData data = (StoreData) existingDataIter.next();
                    if (data.isFCO())
                    {
                        // Catch classes that don't exist (e.g in use by a different app)
                        Class classFound = null;
                        try
                        {
                            classFound = clr.classForName(data.getName());
                        }
                        catch (ClassNotResolvedException cnre)
                        {
                            if (data.getInterfaceName() != null)
                            {
                                try
                                {
                                    getNucleusContext().getImplementationCreator().newInstance(
                                        clr.classForName(data.getInterfaceName()), clr);
                                    classFound = clr.classForName(data.getName());
                                }
                                catch (ClassNotResolvedException cnre2)
                                {
                                    // Do nothing
                                }
                            }
                            // Thrown if class not found
                        }

                        if (classFound != null)
                        {
                            NucleusLogger.PERSISTENCE.info(LOCALISER.msg("032003", data.getName()));
                            classesNeedingAdding.add(data.getName());
                            if (data.getMetaData() == null)
                            {
                                // StoreData doesnt have its metadata set yet so load it
                                // This ensures that the MetaDataManager always knows about these classes
                                AbstractClassMetaData acmd =
                                    getMetaDataManager().getMetaDataForClass(classFound, clr);
                                if (acmd != null)
                                {
                                    data.setMetaData(acmd);
                                }
                                else
                                {
                                    String msg = LOCALISER.msg("034004", data.getName());
                                    if (starter.getMode() == AutoStartMechanism.Mode.CHECKED)
                                    {
                                        NucleusLogger.PERSISTENCE.error(msg);
                                        throw new DatastoreInitialisationException(msg);
                                    }
                                    else if (starter.getMode() == AutoStartMechanism.Mode.IGNORED)
                                    {
                                        NucleusLogger.PERSISTENCE.warn(msg);
                                    }
                                    else if (starter.getMode() == AutoStartMechanism.Mode.QUIET)
                                    {
                                        NucleusLogger.PERSISTENCE.warn(msg);
                                        NucleusLogger.PERSISTENCE.warn(LOCALISER.msg("034001", data.getName()));
                                        starter.deleteClass(data.getName());
                                    }
                                }
                            }
                        }
                        else
                        {
                            String msg = LOCALISER.msg("034000", data.getName());
                            if (starter.getMode() == AutoStartMechanism.Mode.CHECKED)
                            {
                                NucleusLogger.PERSISTENCE.error(msg);
                                throw new DatastoreInitialisationException(msg);
                            }
                            else if (starter.getMode() == AutoStartMechanism.Mode.IGNORED)
                            {
                                NucleusLogger.PERSISTENCE.warn(msg);
                            }
                            else if (starter.getMode() == AutoStartMechanism.Mode.QUIET)
                            {
                                NucleusLogger.PERSISTENCE.warn(msg);
                                NucleusLogger.PERSISTENCE.warn(LOCALISER.msg("034001", data.getName()));
                                starter.deleteClass(data.getName());
                            }
                        }
                    }
                }
                String[] classesToLoad = new String[classesNeedingAdding.size()];
                Iterator classesNeedingAddingIter = classesNeedingAdding.iterator();
                int n = 0;
                while (classesNeedingAddingIter.hasNext())
                {
                    classesToLoad[n++] = (String)classesNeedingAddingIter.next();
                }

                // Load the classes
                try
                {
                    addClasses(classesToLoad, clr);
                }
                catch (Exception e)
                {
                    // Exception while adding so some of the (referenced) classes dont exist
                    NucleusLogger.PERSISTENCE.warn(LOCALISER.msg("034002", e));
                    //if an exception happens while loading AutoStart data, them we disable it, since
                    //it was unable to load the data from AutoStart. The memory state of AutoStart does
                    //not represent the database, and if we don't disable it, we could
                    //think that the autostart store is empty, and we would try to insert new entries in
                    //the autostart store that are already in there
                    illegalState = true;

                    // TODO Go back and add classes one-by-one to eliminate the class(es) with the problem
                }
            }
        }
        finally
        {
            if (starter.isOpen())
            {
                starter.close();
            }
            if (illegalState)
            {
                NucleusLogger.PERSISTENCE.warn(LOCALISER.msg("034003"));
                starter = null;
            }
        }

        starterInitialised = true;
    }

    /**
     * Method to clear the Auto-Starter status.
     */
    protected void clearAutoStarter()
    {
        // AutoStarter - Delete all supported classes
        if (starter != null)
        {
            try
            {
                if (!starter.isOpen())
                {
                    starter.open();
                }
                starter.deleteAllClasses();
            }
            finally
            {
                if (starter.isOpen())
                {
                    starter.close();
                }
            }
        }
    }

    // ------------------------------- Class Management -----------------------------------

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#managesClass(java.lang.String)
     */
    public boolean managesClass(String className)
    {
        return storeDataMgr.managesClass(className);
    }
    
    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#addClass(java.lang.String, org.datanucleus.ClassLoaderResolver)
     */
    public void addClass(String className, ClassLoaderResolver clr)
    {
        addClasses(new String[] {className}, clr);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#addClasses(java.lang.String[], org.datanucleus.ClassLoaderResolver)
     */
    public void addClasses(String[] classNames, ClassLoaderResolver clr)
    {
        if (classNames == null)
        {
            return;
        }

        // Filter out any "simple" type classes
        String[] filteredClassNames = 
            getNucleusContext().getTypeManager().filterOutSupportedSecondClassNames(classNames);

        // Find the ClassMetaData for these classes and all referenced by these classes
        Iterator iter = getMetaDataManager().getReferencedClasses(filteredClassNames, clr).iterator();
        while (iter.hasNext())
        {
            ClassMetaData cmd = (ClassMetaData)iter.next();
            if (cmd.getPersistenceModifier() == ClassPersistenceModifier.PERSISTENCE_CAPABLE)
            {
                if (!storeDataMgr.managesClass(cmd.getFullClassName()))
                {
                    registerStoreData(newStoreData(cmd, clr));
                }
            }
        }
    }

    /**
     * Instantiate a StoreData instance using the provided ClassMetaData and ClassLoaderResolver. 
     * Override this method if you want to instantiate a subclass of StoreData.
     * @param cmd MetaData for the class
     * @param clr ClassLoader resolver
     */
    protected StoreData newStoreData(ClassMetaData cmd, ClassLoaderResolver clr) 
    {
        return new StoreData(cmd.getFullClassName(), cmd, StoreData.FCO_TYPE, null);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#removeAllClasses(org.datanucleus.ClassLoaderResolver)
     */
    public void removeAllClasses(ClassLoaderResolver clr)
    {
        // Do nothing.
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#manageClassForIdentity(java.lang.Object, org.datanucleus.ClassLoaderResolver)
     */
    public String manageClassForIdentity(Object id, ClassLoaderResolver clr)
    {
        String className = null;
        if (id instanceof OID)
        {
            // Check that the implied class is managed
            className = ((OID)id).getPcClass();
            AbstractClassMetaData cmd = getMetaDataManager().getMetaDataForClass(className, clr);
            if (cmd.getIdentityType() != IdentityType.DATASTORE)
            {
                throw new NucleusUserException(LOCALISER.msg("038001", id, cmd.getFullClassName()));
            }
        }
        else if (getApiAdapter().isSingleFieldIdentity(id))
        {
            className = getApiAdapter().getTargetClassNameForSingleFieldIdentity(id);
            AbstractClassMetaData cmd = getMetaDataManager().getMetaDataForClass(className, clr);
            if (cmd.getIdentityType() != IdentityType.APPLICATION || !cmd.getObjectidClass().equals(id.getClass().getName()))
            {
                throw new NucleusUserException(LOCALISER.msg("038001", id, cmd.getFullClassName()));
            }
        }
        else
        {
            throw new NucleusException("StoreManager.manageClassForIdentity called for id=" + id + 
                " yet should only be called for datastore-identity/SingleFieldIdentity");
        }

        // If the class is not yet managed, manage it
        if (!managesClass(className))
        {
            addClass(className, clr);
        }

        return className;
    }

    // ------------------------------ PersistenceManager interface -----------------------------------

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getExtent(org.datanucleus.ObjectManager, java.lang.Class, boolean)
     */
    public Extent getExtent(ExecutionContext ec, Class c, boolean subclasses)
    {
        AbstractClassMetaData cmd = getMetaDataManager().getMetaDataForClass(c, ec.getClassLoaderResolver());
        if (!cmd.isRequiresExtent())
        {
            throw new NoExtentException(c.getName());
        }

        // Create Extent using JDOQL query
        if (!managesClass(c.getName()))
        {
            addClass(c.getName(), ec.getClassLoaderResolver());
        }
        return new DefaultCandidateExtent(ec, c, subclasses, cmd);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#supportsQueryLanguage(java.lang.String)
     */
    public boolean supportsQueryLanguage(String language)
    {
        if (language == null)
        {
            return false;
        }

        // Find if datastore=storeManagerKey has an extension for name="{language}"
        String name = 
            getNucleusContext().getPluginManager().getAttributeValueForExtension(
                "org.datanucleus.store_query_query",
                new String[] {"name", "datastore"},
                new String[]{language, storeManagerKey}, "name");
        return (name != null);
    }

    /**
     * Accessor for whether this value strategy is supported.
     * @param strategy The strategy
     * @return Whether it is supported.
     */
    public boolean supportsValueStrategy(String strategy)
    {
        ConfigurationElement elem =
            nucleusContext.getPluginManager().getConfigurationElementForExtension(
                "org.datanucleus.store_valuegenerator",
                new String[]{"name", "unique"}, 
                new String[] {strategy, "true"});
        if (elem != null)
        {
            // Unique strategy so supported for all datastores
            return true;
        }
        else
        {
            elem = nucleusContext.getPluginManager().getConfigurationElementForExtension(
                "org.datanucleus.store_valuegenerator",
                new String[]{"name", "datastore"},
                new String[] {strategy, storeManagerKey});
            if (elem != null)
            {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getClassNameForObjectID(java.lang.Object, org.datanucleus.ClassLoaderResolver, org.datanucleus.ObjectManager)
     */
    public String getClassNameForObjectID(Object id, ClassLoaderResolver clr, ExecutionContext ec)
    {
        if (id == null)
        {
            // User stupidity
            return null;
        }
        else if (id instanceof SCOID)
        {
            // Object is a SCOID
            return ((SCOID) id).getSCOClass();
        }
        else if (id instanceof OID)
        {
            // Object is an OID
            return ((OID)id).getPcClass();
        }
        else if (getApiAdapter().isSingleFieldIdentity(id))
        {
            // Using SingleFieldIdentity so can assume that object is of the target class
            return getApiAdapter().getTargetClassNameForSingleFieldIdentity(id);
        }
        else
        {
            // Application identity with user PK class, so find all using this PK
            Collection<AbstractClassMetaData> cmds = getMetaDataManager().getClassMetaDataWithApplicationId(
                id.getClass().getName());
            if (cmds != null)
            {
                Iterator<AbstractClassMetaData> iter = cmds.iterator();
                while (iter.hasNext())
                {
                    // Just return the class name of the first one using this id - could be any in this tree
                    AbstractClassMetaData cmd = iter.next();
                    return cmd.getFullClassName();
                }
            }
            return null;
        }
    }

    /**
     * Method to return if a particular value strategy is attributed by the datastore.
     * In this implementation we return true for IDENTITY, and false for others.
     * Override in subclass if the datastore will attribute e.g SEQUENCE in the datastore.
     * @param identityStrategy The strategy
     * @param datastoreIdentityField Whether this is a datastore id field
     * @return Whether the strategy is attributed in the datastore
     */
    public boolean isStrategyDatastoreAttributed(IdentityStrategy identityStrategy, boolean datastoreIdentityField)
    {
        if (identityStrategy == null)
        {
            return false;
        }

        if (identityStrategy == IdentityStrategy.IDENTITY)
        {
            // "identity" is processed in the datastore
            return true;
        }

        return false;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getStrategyValue(org.datanucleus.ObjectManager, org.datanucleus.metadata.AbstractClassMetaData, int)
     */
    public Object getStrategyValue(ExecutionContext ec, AbstractClassMetaData cmd, int absoluteFieldNumber)
    {
        // Extract the control information for this field that needs its value
        AbstractMemberMetaData mmd = null;
        String fieldName = null;
        IdentityStrategy strategy = null;
        String sequence = null;
        String valueGeneratorName = null;
        TableGeneratorMetaData tableGeneratorMetaData = null;
        SequenceMetaData sequenceMetaData = null;
        if (absoluteFieldNumber >= 0)
        {
            // real field
            mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(absoluteFieldNumber);
            fieldName = mmd.getFullFieldName();
            strategy = mmd.getValueStrategy();
            sequence = mmd.getSequence();
            valueGeneratorName = mmd.getValueGeneratorName();
        }
        else
        {
            // datastore-identity surrogate field
            fieldName = cmd.getFullClassName() + " (datastore id)";
            strategy = cmd.getIdentityMetaData().getValueStrategy();
            sequence = cmd.getIdentityMetaData().getSequence();
            valueGeneratorName = cmd.getIdentityMetaData().getValueGeneratorName();
        }

        // Extract any metadata-based generation information
        if (valueGeneratorName != null)
        {
            if (strategy == IdentityStrategy.INCREMENT)
            {
                tableGeneratorMetaData = getMetaDataManager().getMetaDataForTableGenerator(ec.getClassLoaderResolver(), 
                    valueGeneratorName);
                if (tableGeneratorMetaData == null)
                {
                    throw new NucleusUserException(LOCALISER.msg("038005", fieldName, valueGeneratorName));
                }
            }
            else if (strategy == IdentityStrategy.SEQUENCE)
            {
                sequenceMetaData = getMetaDataManager().getMetaDataForSequence(ec.getClassLoaderResolver(), 
                    valueGeneratorName);
                if (sequenceMetaData == null)
                {
                    throw new NucleusUserException(LOCALISER.msg("038006", fieldName, valueGeneratorName));
                }
            }
        }
        else if (strategy == IdentityStrategy.SEQUENCE && sequence != null)
        {
            // TODO Allow for package name of this class prefix for the sequence name
            sequenceMetaData = getMetaDataManager().getMetaDataForSequence(ec.getClassLoaderResolver(), sequence);
            if (sequenceMetaData == null)
            {
                // JPOX 1.1 behaviour was just to use the sequence name in the datastore so log it and fallback to that
                NucleusLogger.VALUEGENERATION.warn("Field " + fieldName + " has been specified to use sequence " + sequence +
                    " but there is no <sequence> specified in the MetaData. " + 
                    "Falling back to use a sequence in the datastore with this name directly.");
            }
        }

        String strategyName = strategy.toString();
        if (strategy.equals(IdentityStrategy.CUSTOM))
        {
            // Using a "custom" generator
            strategyName = strategy.getCustomName();
        }
        else if (strategy.equals(IdentityStrategy.NATIVE))
        {
            strategyName = getStrategyForNative(cmd, absoluteFieldNumber);
        }

        // Value Generators are cached against a name. Some Value Generators are unique per datastore.
        // Others are per class/field. Others have a user-defined name.
        // The name that they are cached under relates to what they use.
        // Generate the name for ValueGenerationManager to use the strategy for this field/class.
        String generatorName = null;
        String generatorNameKeyInManager = null;
        ConfigurationElement elem =
            nucleusContext.getPluginManager().getConfigurationElementForExtension(
                "org.datanucleus.store_valuegenerator",
                new String[]{"name", "unique"}, new String[] {strategyName, "true"});
        if (elem != null)
        {
            // Unique value generator so set the key in ValueGenerationManager to the value generator name itself
            generatorName = elem.getAttribute("name");
            generatorNameKeyInManager = generatorName;
        }
        else
        {
            // Not a unique (datastore-independent) generator so try just for this datastore
            elem = nucleusContext.getPluginManager().getConfigurationElementForExtension(
                "org.datanucleus.store_valuegenerator", 
                new String[]{"name", "datastore"}, new String[] {strategyName, storeManagerKey});
            if (elem != null)
            {
                // Set the generator name (for use by the ValueGenerationManager)
                generatorName = elem.getAttribute("name");
            }
        }
        if (generatorNameKeyInManager == null)
        {
            // Value generator is not unique so set based on class/field
            if (absoluteFieldNumber >= 0)
            {
                // Require generation for a field so use field name for the generator symbolic name
                generatorNameKeyInManager = mmd.getFullFieldName();
            }
            else
            {
                // Require generation for a datastore id field so use the base class name for generator symbolic name
                generatorNameKeyInManager = cmd.getBaseAbstractClassMetaData().getFullClassName();
            }
        }

        // Try to find the generator from the ValueGenerationManager if we already have it managed
        ValueGenerator generator = null;
        synchronized (this)
        {
            // This block is synchronised since we don't want to let any other thread through until
            // the generator is created, in case it is the same as the next request
            generator = getValueGenerationManager().getValueGenerator(generatorNameKeyInManager);
            if (generator == null)
            {
                if (generatorName == null)
                {
                    // No available value-generator for the specified strategy for this datastore
                    throw new NucleusUserException(LOCALISER.msg("038004", strategy));
                }

                // Set up the default properties available for all value generators
                Properties props = getPropertiesForGenerator(cmd, absoluteFieldNumber, ec, sequenceMetaData,
                    tableGeneratorMetaData);

                Class cls = null;
                if (elem != null)
                {
                    cls = nucleusContext.getPluginManager().loadClass(elem.getExtension().getPlugin().getSymbolicName(), 
                        elem.getAttribute("class-name"));
                }
                if (cls == null)
                {
                    throw new NucleusException("Cannot create Value Generator for strategy " + generatorName);
                }

                // Create the new ValueGenerator since the first time required (registers it with 
                // the ValueGenerationManager too)
                generator = getValueGenerationManager().createValueGenerator(generatorNameKeyInManager, cls, props, this, null);
            }
        }

        // Get the next value from the generator
        Object oid = getStrategyValueForGenerator(generator, ec);

        if (mmd != null)
        {
            // replace the value of the id, but before convert the value to the field type if needed
            try
            {
                Object convertedValue = TypeConversionHelper.convertTo(oid, mmd.getType());
                if (convertedValue == null)
                {
                    throw new NucleusException(LOCALISER.msg("038003", mmd.getFullFieldName(), oid)).setFatal();
                }
                oid = convertedValue;
            }
            catch (NumberFormatException nfe)
            {
                throw new NucleusUserException("Value strategy created value="+oid+" type="+oid.getClass().getName() +
                        " but field is of type "+mmd.getTypeName() + ". Use a different strategy or change the type of the field " + mmd.getFullFieldName());
            }
        }

        if (NucleusLogger.VALUEGENERATION.isDebugEnabled())
        {
            NucleusLogger.VALUEGENERATION.debug(LOCALISER.msg("038002", fieldName, strategy, generator.getClass().getName(), oid));
        }

        return oid;
    }

    /**
     * Method defining which value-strategy to use when the user specifies "native".
     * Returns "uuid-hex" no matter what the field is since this is built-in and available for all.
     * Override if your datastore requires something else.
     * @param cmd Class requiring the strategy
     * @param absFieldNumber Field of the class
     * @return Just returns "uuid-hex".
     */
    protected String getStrategyForNative(AbstractClassMetaData cmd, int absFieldNumber)
    {
        if (absFieldNumber >= 0)
        {
            AbstractMemberMetaData mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(absFieldNumber);
            Class type = mmd.getType();
            if (String.class.isAssignableFrom(type))
            {
                return "uuid-hex";
            }
            else if (type == Long.class || type == Integer.class || type == Short.class || 
                type == long.class || type == int.class || type == short.class)
            {
                if (supportsValueStrategy("increment"))
                {
                    return "increment";
                }
            }

            throw new NucleusUserException("This datastore provider doesn't support native strategy for field of type " + type.getName());
        }

        // TODO Handle datastore id of numeric type
        return "uuid-hex";
    }

    /**
     * Accessor for the next value from the specified generator.
     * This implementation simply returns generator.next(). Any case where the generator requires
     * datastore connections should override this method.
     * @param generator The generator
     * @param ec execution context
     * @return The next value.
     */
    protected Object getStrategyValueForGenerator(ValueGenerator generator, final ExecutionContext ec)
    {
        Object oid = null;
        synchronized (generator)
        {
            // Get the next value for this generator for this ObjectManager
            // Note : this is synchronised since we don't want to risk handing out this generator
            // while its connectionProvider is set to that of a different ObjectManager
            // It maybe would be good to change ValueGenerator to have a next taking the connectionProvider
            if (generator instanceof AbstractDatastoreGenerator)
            {
                // datastore-based generator so set the connection provider, using connection for PM
                ValueGenerationConnectionProvider connProvider = new ValueGenerationConnectionProvider()
                {
                    ManagedConnection mconn;
                    public ManagedConnection retrieveConnection()
                    {
                        mconn = getConnection(ec);
                        return mconn;
                    }
                    public void releaseConnection() 
                    {
                        mconn.release();
                        mconn = null;
                    }
                };
                ((AbstractDatastoreGenerator)generator).setConnectionProvider(connProvider);
            }

            oid = generator.next();
        }
        return oid;
    }

    /**
     * Method to return the properties to pass to the generator for the specified field.
     * Will define the following properties "class-name", "root-class-name", "field-name" (if for a field),
     * "sequence-name", "key-initial-value", "key-cache-size", "sequence-table-name", "sequence-schema-name",
     * "sequence-catalog-name", "sequence-name-column-name", "sequence-nextval-column-name".
     * In addition any extension properties on the respective field or datastore-identity are also passed through 
     * as properties.
     * @param cmd MetaData for the class
     * @param absoluteFieldNumber Number of the field (-1 = datastore identity)
     * @param ec execution context
     * @param seqmd Any sequence metadata
     * @param tablegenmd Any table generator metadata
     * @return The properties to use for this field
     */
    protected Properties getPropertiesForGenerator(AbstractClassMetaData cmd, int absoluteFieldNumber,
            ExecutionContext ec, SequenceMetaData seqmd, TableGeneratorMetaData tablegenmd)
    {
        // Set up the default properties available for all value generators
        Properties properties = new Properties();
        AbstractMemberMetaData mmd = null;
        IdentityStrategy strategy = null;
        String sequence = null;
        ExtensionMetaData[] extensions = null;
        if (absoluteFieldNumber >= 0)
        {
            // real field
            mmd = cmd.getMetaDataForManagedMemberAtAbsolutePosition(absoluteFieldNumber);
            strategy = mmd.getValueStrategy();
            sequence = mmd.getSequence();
            extensions = mmd.getExtensions();
        }
        else
        {
            // datastore-identity surrogate field
            // always use the root IdentityMetaData since the root class defines the identity
            IdentityMetaData idmd = cmd.getBaseIdentityMetaData();
            strategy = idmd.getValueStrategy();
            sequence = idmd.getSequence();
            extensions = idmd.getExtensions();
        }

        properties.setProperty("class-name", cmd.getFullClassName());
        properties.put("root-class-name", cmd.getBaseAbstractClassMetaData().getFullClassName());
        if (mmd != null)
        {
            properties.setProperty("field-name", mmd.getFullFieldName());
        }

        if (sequence != null)
        {
            properties.setProperty("sequence-name", sequence);
        }

        // Add any extension properties
        if (extensions != null)
        {
            for (int i=0;i<extensions.length;i++)
            {
                properties.put(extensions[i].getKey(), extensions[i].getValue());
            }
        }

        if (strategy == IdentityStrategy.INCREMENT && tablegenmd != null)
        {
            // User has specified a TableGenerator (JPA)
            properties.put("key-initial-value", "" + tablegenmd.getInitialValue());
            properties.put("key-cache-size", "" + tablegenmd.getAllocationSize());
            if (tablegenmd.getTableName() != null)
            {
                properties.put("sequence-table-name", tablegenmd.getTableName());
            }
            if (tablegenmd.getCatalogName() != null)
            {
                properties.put("sequence-catalog-name", tablegenmd.getCatalogName());
            }
            if (tablegenmd.getSchemaName() != null)
            {
                properties.put("sequence-schema-name", tablegenmd.getSchemaName());
            }
            if (tablegenmd.getPKColumnName() != null)
            {
                properties.put("sequence-name-column-name", tablegenmd.getPKColumnName());
            }
            if (tablegenmd.getPKColumnName() != null)
            {
                properties.put("sequence-nextval-column-name", tablegenmd.getValueColumnName());
            }
            if (tablegenmd.getPKColumnValue() != null)
            {
                properties.put("sequence-name", tablegenmd.getPKColumnValue());
            }
        }
        else if (strategy == IdentityStrategy.INCREMENT && tablegenmd == null)
        {
            if (!properties.containsKey("key-cache-size"))
            {
                // Use default allocation size
                int allocSize = getIntProperty("datanucleus.valuegeneration.increment.allocationSize");
                properties.put("key-cache-size", "" + allocSize);
            }
        }
        else if (strategy == IdentityStrategy.SEQUENCE && seqmd != null)
        {
            // User has specified a SequenceGenerator (JDO/JPA)
            if (seqmd.getDatastoreSequence() != null)
            {
                if (seqmd.getInitialValue() >= 0)
                {
                    properties.put("key-initial-value", "" + seqmd.getInitialValue());
                }
                if (seqmd.getAllocationSize() > 0)
                {
                    properties.put("key-cache-size", "" + seqmd.getAllocationSize());
                }
                else
                {
                    // Use default allocation size
                    int allocSize = getIntProperty("datanucleus.valuegeneration.sequence.allocationSize");
                    properties.put("key-cache-size", "" + allocSize);
                }
                properties.put("sequence-name", "" + seqmd.getDatastoreSequence());

                // Add on any extensions specified on the sequence
                ExtensionMetaData[] seqExtensions = seqmd.getExtensions();
                if (seqExtensions != null)
                {
                    for (int i=0;i<seqExtensions.length;i++)
                    {
                        properties.put(seqExtensions[i].getKey(), seqExtensions[i].getValue());
                    }
                }
            }
            else
            {
                // JDO Factory-based sequence generation
                // TODO Support this
            }
        }
        return properties;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#getSubClassesForClass(java.lang.String, boolean, org.datanucleus.ClassLoaderResolver)
     */
    public HashSet<String> getSubClassesForClass(String className, boolean includeDescendents, ClassLoaderResolver clr)
    {
        HashSet subclasses = new HashSet();

        String[] subclassNames = getMetaDataManager().getSubclassesForClass(className, includeDescendents);
        if (subclassNames != null)
        {
            // Load up the table for any classes that are not yet loaded
            for (int i=0;i<subclassNames.length;i++)
            {
                if (!storeDataMgr.managesClass(subclassNames[i]))
                {
                    // We have no knowledge of this class so load it now
                    addClass(subclassNames[i], clr);
                }
                subclasses.add(subclassNames[i]);
            }
        }

        return subclasses;
    }

    /**
     * Accessor for the supported options in string form.
     * Typical values specified here are :-
     * <ul>
     * <li>ApplicationIdentity - if the datastore supports application identity</li>
     * <li>DatastoreIdentity - if the datastore supports datastore identity</li>
     * <li>ORM - if the datastore supports (some) ORM concepts</li>
     * <li>TransactionIsolationLevel.read-committed - if supporting this txn isolation level</li>
     * <li>TransactionIsolationLevel.read-uncommitted - if supporting this txn isolation level</li>
     * <li>TransactionIsolationLevel.repeatable-read - if supporting this txn isolation level</li>
     * <li>TransactionIsolationLevel.serializable - if supporting this txn isolation level</li>
     * <li>TransactionIsolationLevel.snapshot - if supporting this txn isolation level</li>
     * <li>Query.Cancel - if supporting cancelling of queries</li>
     * <li>Query.Timeout - if supporting timeout of queries</li>
     * </ul>
     */
    public Collection<String> getSupportedOptions()
    {
        return Collections.EMPTY_SET;
    }

    /**
     * Convenience method to assert when this StoreManager is read-only and the specified object
     * is attempting to be updated.
     * @param op ObjectProvider for the object
     */
    public void assertReadOnlyForUpdateOfObject(ObjectProvider op)
    {
        if (readOnlyDatastore)
        {
            if (getStringProperty("datanucleus.readOnlyDatastoreAction").equalsIgnoreCase("EXCEPTION"))
            {
                throw new DatastoreReadOnlyException(LOCALISER.msg("032004",
                    op.toPrintableID()), op.getExecutionContext().getClassLoaderResolver());
            }
            else
            {
                if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                {
                    NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("032005", op.toPrintableID()));
                }
                return;
            }
        }
        else
        {
            AbstractClassMetaData cmd = op.getClassMetaData();
            if (cmd.hasExtension("read-only"))
            {
                String value = cmd.getValueForExtension("read-only");
                if (!StringUtils.isWhitespace(value))
                {
                    boolean readonly = Boolean.valueOf(value).booleanValue();
                    if (readonly)
                    {
                        if (getStringProperty("datanucleus.readOnlyDatastoreAction").equalsIgnoreCase("EXCEPTION"))
                        {
                            throw new DatastoreReadOnlyException(LOCALISER.msg("032006",
                                op.toPrintableID()), op.getExecutionContext().getClassLoaderResolver());
                        }
                        else
                        {
                            if (NucleusLogger.PERSISTENCE.isDebugEnabled())
                            {
                                NucleusLogger.PERSISTENCE.debug(LOCALISER.msg("032007", op.toPrintableID()));
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.properties.TypedPropertyStore#getProperty(java.lang.String)
     */
    @Override
    public Object getProperty(String name)
    {
        // Use local property value if present, otherwise relay back to context property value
        if (properties.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            return super.getProperty(name);
        }
        return nucleusContext.getPersistenceConfiguration().getProperty(name);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.properties.PropertyStore#getIntProperty(java.lang.String)
     */
    @Override
    public int getIntProperty(String name)
    {
        // Use local property value if present, otherwise relay back to context property value
        if (properties.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            return super.getIntProperty(name);
        }
        return nucleusContext.getPersistenceConfiguration().getIntProperty(name);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.properties.PropertyStore#getStringProperty(java.lang.String)
     */
    @Override
    public String getStringProperty(String name)
    {
        // Use local property value if present, otherwise relay back to context property value
        if (properties.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            return super.getStringProperty(name);
        }
        return nucleusContext.getPersistenceConfiguration().getStringProperty(name);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.properties.PropertyStore#getBooleanProperty(java.lang.String)
     */
    @Override
    public boolean getBooleanProperty(String name)
    {
        // Use local property value if present, otherwise relay back to context property value
        if (properties.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            return super.getBooleanProperty(name);
        }
        return nucleusContext.getPersistenceConfiguration().getBooleanProperty(name);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.properties.PropertyStore#getBooleanProperty(java.lang.String, boolean)
     */
    @Override
    public boolean getBooleanProperty(String name, boolean resultIfNotSet)
    {
        // Use local property value if present, otherwise relay back to context property value
        if (properties.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            return super.getBooleanProperty(name, resultIfNotSet);
        }
        return nucleusContext.getPersistenceConfiguration().getBooleanProperty(name, resultIfNotSet);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.properties.PropertyStore#getBooleanObjectProperty(java.lang.String)
     */
    @Override
    public Boolean getBooleanObjectProperty(String name)
    {
        // Use local property value if present, otherwise relay back to context property value
        if (properties.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            return super.getBooleanObjectProperty(name);
        }
        return nucleusContext.getPersistenceConfiguration().getBooleanObjectProperty(name);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#transactionStarted(org.datanucleus.store.ExecutionContext)
     */
    public void transactionStarted(ExecutionContext ec)
    {
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#transactionCommitted(org.datanucleus.store.ExecutionContext)
     */
    public void transactionCommitted(ExecutionContext ec)
    {
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.StoreManager#transactionRolledBack(org.datanucleus.store.ExecutionContext)
     */
    public void transactionRolledBack(ExecutionContext ec)
    {
    }
}