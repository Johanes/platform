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
2004 Joerg Van Frantzius - changes to support a form of DDL output
2004 Erik Bengtson - dbinfo() mode
2004 Andy Jefferson - added "mapping" property to allow ORM files
2010 Andy Jefferson - rewritten the commandline interface to not need Context etc
    ...
**********************************************************************/
package org.datanucleus.store.schema;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.NucleusContext;
import org.datanucleus.PersistenceConfiguration;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.FileMetaData;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.metadata.MetaDataUtils;
import org.datanucleus.metadata.PersistenceUnitMetaData;
import org.datanucleus.store.StoreManager;
import org.datanucleus.store.schema.SchemaAwareStoreManager;
import org.datanucleus.util.CommandLine;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.PersistenceUtils;
import org.datanucleus.util.StringUtils;

/**
 * SchemaTool providing an interface for the maintenance of schemas.
 * These utilities include:-
 * <ul>
 * <li>creation of tables representing classes specified in input data</li>
 * <li>deletion of tables representing classes specified in input data</li>
 * <li>validation of tables representing classes specified in input data</li>
 * <li>details about the datastore</li>
 * </ul>
 */
public class SchemaTool
{
    /** Localiser for messages. */
    protected static final Localiser LOCALISER=Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /** Name of the persistence API to use. */
    private String apiName = "JDO";

    /** Name of a file in which to put the DDL (or null if wanting to execute in the datastore). */
    private String ddlFilename = null;

    /** When generating DDL to a file, whether to generate complete DDL, or just for missing components. */
    private boolean completeDdl = false;

    /** When updating the schema, whether to include any auto-start mechanism. */
    private boolean includeAutoStart = false;

    /** Whether to operate in verbose mode. */
    private boolean verbose = false;

    /** create mode **/
    public static final int SCHEMATOOL_CREATE_MODE = 1;

    /** delete mode **/
    public static final int SCHEMATOOL_DELETE_MODE = 2;

    /** validate mode **/
    public static final int SCHEMATOOL_VALIDATE_MODE = 3;

    /** database info mode **/
    public static final int SCHEMATOOL_DATABASE_INFO_MODE = 4;

    /** schema info mode **/
    public static final int SCHEMATOOL_SCHEMA_INFO_MODE = 5;

    public static final NucleusLogger LOGGER = NucleusLogger.getLoggerInstance("DataNucleus.SchemaTool");

    /**
     * Entry method when invoked from the command line.
     * @param args List of options for processing by the available methods in this class.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception
    {
        SchemaTool tool = new SchemaTool();

        CommandLine cmd = new CommandLine();
        cmd.addOption("create", "create", null, LOCALISER.msg(false, "014026"));
        cmd.addOption("delete", "delete", null, LOCALISER.msg(false, "014027"));
        cmd.addOption("validate", "validate", null, LOCALISER.msg(false, "014028"));
        cmd.addOption("dbinfo", "dbinfo", null, LOCALISER.msg(false, "014029"));
        cmd.addOption("schemainfo", "schemainfo", null, LOCALISER.msg(false, "014030"));
        cmd.addOption("help", "help", null, LOCALISER.msg(false, "014033"));

        cmd.addOption("ddlFile", "ddlFile", "ddlFile", LOCALISER.msg(false, "014031"));
        cmd.addOption("completeDdl", "completeDdl", null, LOCALISER.msg(false, "014032"));

        cmd.addOption("includeAutoStart", "includeAutoStart", null, "Include Auto-Start Mechanisms");

        cmd.addOption("api", "api", "api", "API Adapter (JDO, JPA, etc)");
        cmd.addOption("v", "verbose", null, "verbose output");

        cmd.addOption("pu", "persistenceUnit", "<persistence-unit>", 
            "name of the persistence unit to handle the schema for");
        cmd.addOption("props", "properties", "props", "path to a properties file");
        cmd.parse(args);

        // Remaining command line args are filenames (class files, metadata files)
        String[] filenames = cmd.getDefaultArgs();

        if (cmd.hasOption("api"))
        {
            tool.setApi(cmd.getOptionArg("api"));
        }

        // Determine the mode of operation required
        String msg = null;
        int mode = SCHEMATOOL_CREATE_MODE;
        if (cmd.hasOption("create"))
        {
            mode = SCHEMATOOL_CREATE_MODE;
            msg = LOCALISER.msg(false, "014000");
        }
        else if (cmd.hasOption("delete"))
        {
            mode = SCHEMATOOL_DELETE_MODE;
            msg = LOCALISER.msg(false, "014001");
        }
        else if (cmd.hasOption("validate"))
        {
            mode = SCHEMATOOL_VALIDATE_MODE;
            msg = LOCALISER.msg(false, "014002");
        }
        else if (cmd.hasOption("dbinfo"))
        {
            mode = SCHEMATOOL_DATABASE_INFO_MODE;
            msg = LOCALISER.msg(false, "014003");
        }
        else if (cmd.hasOption("schemainfo"))
        {
            mode = SCHEMATOOL_SCHEMA_INFO_MODE;
            msg = LOCALISER.msg(false, "014004");
        }
        else if (cmd.hasOption("help"))
        {
            System.out.println(LOCALISER.msg(false, "014023"));
            System.out.println(LOCALISER.msg(false, "014024"));
            System.out.println(LOCALISER.msg(false, "014025"));
            System.out.println(cmd.toString());
            System.out.println(LOCALISER.msg(false, "014034"));
            System.out.println(LOCALISER.msg(false, "014035"));
            System.exit(0);
        }
        LOGGER.info(msg);
        System.out.println(msg);

        // Extract the selected options
        String propsFileName = null;
        String persistenceUnitName = null;
        if (cmd.hasOption("ddlFile"))
        {
            tool.setDdlFile(cmd.getOptionArg("ddlFile"));
        }
        if (cmd.hasOption("completeDdl"))
        {
            tool.setCompleteDdl(true);
        }
        if (cmd.hasOption("includeAutoStart"))
        {
            tool.setIncludeAutoStart(true);
        }
        if (cmd.hasOption("v"))
        {
            tool.setVerbose(true);
        }

        if (cmd.hasOption("pu"))
        {
            persistenceUnitName = cmd.getOptionArg("pu");
        }
        if (cmd.hasOption("props"))
        {
            propsFileName = cmd.getOptionArg("props");
        }

        // Classpath
        msg = LOCALISER.msg(false, "014005");
        LOGGER.info(msg);
        if (tool.isVerbose())
        {
            System.out.println(msg);
        }
        StringTokenizer tokeniser = new StringTokenizer(System.getProperty("java.class.path"), File.pathSeparator);
        while (tokeniser.hasMoreTokens())
        {
            msg = LOCALISER.msg(false, "014006", tokeniser.nextToken());
            LOGGER.info(msg);
            if (tool.isVerbose())
            {
                System.out.println(msg);
            }
        }
        if (tool.isVerbose())
        {
            System.out.println();
        }

        // DDL file
        String ddlFilename = tool.getDdlFile();
        if (ddlFilename != null)
        {
            msg = LOCALISER.msg(false, tool.getCompleteDdl() ? "014018" : "014019", ddlFilename);
            LOGGER.info(msg);
            if (tool.isVerbose())
            {
                System.out.println(msg);
                System.out.println();
            }
        }

        // Create a NucleusContext for use with this mode
        NucleusContext nucleusCtx = null;
        try
        {
            if (propsFileName != null)
            {
                Properties props = PersistenceUtils.setPropertiesUsingFile(propsFileName);
                nucleusCtx = getNucleusContextForMode(mode, tool.getApi(), props, persistenceUnitName, 
                    ddlFilename, tool.isVerbose());
            }
            else
            {
                nucleusCtx = getNucleusContextForMode(mode, tool.getApi(), null, persistenceUnitName, 
                    ddlFilename, tool.isVerbose());
            }
        }
        catch (Exception e)
        {
            // Unable to create a NucleusContext so likely input errors
            LOGGER.error("Error creating NucleusContext", e);
            System.out.println(LOCALISER.msg(false, "014008", e.getMessage()));
            System.exit(1);
            return;
        }

        Set<String> classNames = null;
        if (mode != SCHEMATOOL_SCHEMA_INFO_MODE && mode != SCHEMATOOL_DATABASE_INFO_MODE)
        {
            // Find the names of the classes to be processed
            // This will load up all MetaData for the specified input and throw exceptions where errors are found
            try
            {
                MetaDataManager metaDataMgr = nucleusCtx.getMetaDataManager();
                ClassLoaderResolver clr = nucleusCtx.getClassLoaderResolver(null);

                if (filenames == null && persistenceUnitName == null)
                {
                    msg = LOCALISER.msg(false, "014007");
                    LOGGER.error(msg);
                    System.out.println(msg);
                    throw new NucleusUserException(msg);
                }

                FileMetaData[] filemds = null;
                if (persistenceUnitName != null)
                {
                    // Schema management via "persistence-unit"
                    msg = LOCALISER.msg(false, "014015", persistenceUnitName);
                    LOGGER.info(msg);
                    if (tool.isVerbose())
                    {
                        System.out.println(msg);
                        System.out.println();
                    }

                    // The NucleusContext will have initialised the MetaDataManager with the persistence-unit
                    filemds = metaDataMgr.getFileMetaData();
                }
                else
                {
                    // Schema management via "Input Files" (metadata/class)
                    msg = LOCALISER.msg(false, "014009");
                    LOGGER.info(msg);
                    if (tool.isVerbose())
                    {
                        System.out.println(msg);
                    }
                    for (int i = 0; i < filenames.length; i++)
                    {
                        String entry = LOCALISER.msg(false, "014010", filenames[i]);
                        LOGGER.info(entry);
                        if (tool.isVerbose())
                        {
                            System.out.println(entry);
                        }
                    }
                    if (tool.isVerbose())
                    {
                        System.out.println();
                    }

                    LOGGER.debug(LOCALISER.msg(false, "014011", "" + filenames.length));
                    filemds = MetaDataUtils.getFileMetaDataForInputFiles(metaDataMgr, clr, filenames);
                    LOGGER.debug(LOCALISER.msg(false, "014012", "" + filenames.length));
                }

                classNames = new TreeSet<String>();
                if (filemds == null)
                {
                    msg = LOCALISER.msg(false, "014021");
                    LOGGER.error(msg);
                    System.out.println(msg);
                    System.exit(2);
                    return;
                }
                for (int i=0;i<filemds.length;i++)
                {
                    for (int j=0;j<filemds[i].getNoOfPackages();j++)
                    {
                        for (int k=0;k<filemds[i].getPackage(j).getNoOfClasses();k++)
                        {
                            String className = filemds[i].getPackage(j).getClass(k).getFullClassName();
                            if (!classNames.contains(className))
                            {
                                classNames.add(className);
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                // Exception will have been logged and sent to System.out in "getFileMetaDataForInput()"
                System.exit(2);
                return;
            }
        }

        // Run SchemaTool
        StoreManager storeMgr = nucleusCtx.getStoreManager();
        if (!(storeMgr instanceof SchemaAwareStoreManager))
        {
            LOGGER.error("StoreManager of type " + storeMgr.getClass().getName() +
                " is not schema-aware so cannot be used with SchemaTool");
            System.exit(2);
            return;
        }
        SchemaAwareStoreManager schemaStoreMgr = (SchemaAwareStoreManager) storeMgr;

        try
        {
            if (mode == SCHEMATOOL_CREATE_MODE)
            {
                tool.createSchema(schemaStoreMgr, classNames);
            }
            else if (mode == SCHEMATOOL_DELETE_MODE)
            {
                tool.deleteSchema(schemaStoreMgr, classNames);
            }
            else if (mode == SCHEMATOOL_VALIDATE_MODE)
            {
                tool.validateSchema(schemaStoreMgr, classNames);
            }
            else if (mode == SCHEMATOOL_DATABASE_INFO_MODE)
            {
                storeMgr.printInformation("DATASTORE", System.out);
            }
            else if (mode == SCHEMATOOL_SCHEMA_INFO_MODE)
            {
                storeMgr.printInformation("SCHEMA", System.out);
            }

            msg = LOCALISER.msg(false, "014043");
            LOGGER.info(msg);
            System.out.println(msg);
        }
        catch (Exception e)
        {
            msg = LOCALISER.msg(false, "014037", e.getMessage());
            System.out.println(msg);
            LOGGER.error(msg, e);
            System.exit(2);
            return;
        }
    }

    /**
     * Constructor
     */
    public SchemaTool()
    {
    }

    /**
     * Method to generate the properties to be used by SchemaTool.
     * This includes whether to create DDL to a file, and whether to include any auto-start mechanism
     * @return The properties to use with SchemaTool.
     */
    public Properties getPropertiesForSchemaTool()
    {
        Properties props = new Properties();
        if (getDdlFile() != null)
        {
            props.setProperty("ddlFilename", getDdlFile());
        }
        if (getCompleteDdl())
        {
            props.setProperty("completeDdl", "true");
        }
        if (getIncludeAutoStart())
        {
            props.setProperty("autoStartTable", "true");
        }
        return props;
    }

    public void createSchema(SchemaAwareStoreManager storeMgr, Set<String> classNames)
    {
        storeMgr.createSchema(classNames, getPropertiesForSchemaTool());
    }

    public void deleteSchema(SchemaAwareStoreManager storeMgr, Set<String> classNames)
    {
        storeMgr.deleteSchema(classNames, getPropertiesForSchemaTool());
    }

    public void validateSchema(SchemaAwareStoreManager storeMgr, Set<String> classNames)
    {
        storeMgr.validateSchema(classNames, getPropertiesForSchemaTool());
    }

    /**
     * Method to create a NucleusContext for the specified mode of SchemaTool
     * @param mode Mode of operation of SchemaTool
     * @param api Persistence API
     * @param userProps Map containing user provided properties (usually input via a file)
     * @param persistenceUnitName Name of the persistence-unit (if any)
     * @param ddlFile Name of a file to output DDL to
     * @param verbose Verbose mode
     * @return The NucleusContext to use
     * @throws NucleusException Thrown if an error occurs in creating the required NucleusContext
     */
    protected static NucleusContext getNucleusContextForMode(int mode, String api, Map userProps, 
            String persistenceUnitName, String ddlFile, boolean verbose)
    {
        // Extract any properties that affect NucleusContext startup
        Map startupProps = null;
        if (userProps != null)
        {
            // Possible properties to check for
            for (String startupPropName : NucleusContext.STARTUP_PROPERTIES)
            {
                if (userProps.containsKey(startupPropName))
                {
                    if (startupProps == null)
                    {
                        startupProps = new HashMap();
                    }
                    startupProps.put(startupPropName, userProps.get(startupPropName));
                }
            }
        }

        // Initialise the context for this API
        NucleusContext nucleusCtx = new NucleusContext(api, startupProps);
        PersistenceConfiguration propConfig = nucleusCtx.getPersistenceConfiguration();

        // Generate list of properties for SchemaTool usage
        Map props = new HashMap();
        PersistenceUnitMetaData pumd = null;
        if (persistenceUnitName != null)
        {
            // Obtain any props defined for the persistence-unit
            props.put("javax.jdo.option.persistenceunitname", persistenceUnitName);
            pumd = nucleusCtx.getMetaDataManager().getMetaDataForPersistenceUnit(persistenceUnitName);
            if (pumd != null)
            {
                // Add the properties for the unit
                if (pumd.getProperties() != null)
                {
                    props.putAll(pumd.getProperties());
                }
            }
            else
            {
                throw new NucleusUserException("SchemaTool has been specified to use persistence-unit with name " + 
                    persistenceUnitName + " but none was found with that name");
            }

            if (api.equalsIgnoreCase("JPA"))
            {
                pumd.clearJarFiles(); // Don't use JARs when in J2SE for JPA
            }
        }
        else if (userProps != null)
        {
            // Properties specified by the user in a file
            for (Object key : userProps.keySet())
            {
                String propName = (String)key;
                props.put(propName.toLowerCase(Locale.ENGLISH), userProps.get(propName));
            }
        }
        else
        {
            // Properties specified via System properties (only support particular ones, and in correct case)
            String[] propNames = 
            {
                    "datanucleus.ConnectionURL",
                    "datanucleus.ConnectionDriverName",
                    "datanucleus.ConnectionUserName",
                    "datanucleus.ConnectionPassword",
                    "datanucleus.Mapping",
                    "javax.jdo.option.ConnectionURL",
                    "javax.jdo.option.ConnectionDriverName",
                    "javax.jdo.option.ConnectionUserName",
                    "javax.jdo.option.ConnectionPassword",
                    "javax.jdo.option.Mapping"
            };
            for (int i=0;i<propNames.length;i++)
            {
                if (System.getProperty(propNames[i]) != null)
                {
                    props.put(propNames[i].toLowerCase(Locale.ENGLISH), System.getProperty(propNames[i]));
                }
            }
        }

        props.put("datanucleus.autostartmechanism", "None"); // Interferes with usage

        // Tag on the mandatory props that we must have for each mode
        if (mode == SCHEMATOOL_CREATE_MODE)
        {
            if (ddlFile != null)
            {
                // the tables must not be created in the DB, so do not validate (DDL is being output to a file)
                props.put("datanucleus.validateconstraints", "false");
                props.put("datanucleus.validatecolumns", "false");
                props.put("datanucleus.validatetables", "false");
            }
            props.remove("datanucleus.autocreateschema"); // use tables/columns/constraints settings
            if (!props.containsKey("datanucleus.autocreatetables"))
            {
                props.put("datanucleus.autocreatetables", "true");
            }
            if (!props.containsKey("datanucleus.autocreatecolumns"))
            {
                props.put("datanucleus.autocreatecolumns", "true");
            }
            if (!props.containsKey("datanucleus.autocreateconstraints"))
            {
                props.put("datanucleus.autocreateconstraints", "true");
            }
            props.put("datanucleus.fixeddatastore", "false");
            props.put("datanucleus.readonlydatastore", "false");
            props.put("datanucleus.rdbms.checkexisttablesorviews", "true");
        }
        else if (mode == SCHEMATOOL_DELETE_MODE)
        {
            props.put("datanucleus.fixeddatastore", "false");
            props.put("datanucleus.readonlydatastore", "false");
        }
        else if (mode == SCHEMATOOL_VALIDATE_MODE)
        {
            props.put("datanucleus.autocreateschema", "false");
            props.put("datanucleus.autocreatetables", "false");
            props.put("datanucleus.autocreateconstraints", "false");
            props.put("datanucleus.autocreatecolumns", "false");
            props.put("datanucleus.validatetables", "true");
            props.put("datanucleus.validatecolumns", "true");
            props.put("datanucleus.validateconstraints", "true");
        }

        // Apply remaining persistence properties
        propConfig.setPersistenceProperties(props);

        if (pumd != null)
        {
            // Initialise the MetaDataManager with all files/classes for this persistence-unit
            // This is done now that all persistence properties are set (including the persistence-unit props)
            nucleusCtx.getMetaDataManager().loadPersistenceUnit(pumd, null);
        }

        // Initialise the NucleusContext for use
        nucleusCtx.initialise();

        if (verbose)
        {
            String msg = LOCALISER.msg(false, "014020");
            LOGGER.info(msg);
            System.out.println(msg);

            // TODO Some persistence properties will be stored against the StoreManager
            Map<String,Object> pmfProps = propConfig.getPersistenceProperties();
            Set<String> keys = pmfProps.keySet();
            List<String> keyNames = new ArrayList<String>(keys);
            Collections.sort(keyNames);
            Iterator keyNamesIter = keyNames.iterator();
            while (keyNamesIter.hasNext())
            {
                String key = (String)keyNamesIter.next();
                Object value = pmfProps.get(key);
                boolean display = true;
                if (!key.startsWith("datanucleus"))
                {
                    display = false;
                }
                else if (key.equals("datanucleus.connectionpassword"))
                {
                    // Don't show passwords
                    display = false;
                }
                else if (value == null)
                {
                    display = false;
                }
                else if (value instanceof String && StringUtils.isWhitespace((String)value))
                {
                    display = false;
                }

                if (display)
                {
                    // Print the property to sysout
                    msg = LOCALISER.msg(false, "014022", key, value);
                    LOGGER.info(msg);
                    System.out.println(msg);
                }
            }
            System.out.println();            
        }

        return nucleusCtx;
    }

    /**
     * Acessor for the metadata API (JDO, JPA) in use (metadata definition)
     * @return the API
     */
    public String getApi()
    {
        return apiName;
    }

    /**
     * Mutator for the metadata API (JDO, JPA)
     * @param api the API
     * @return The SchemaTool instance
     */
    public SchemaTool setApi(String api)
    {
        this.apiName = api;
        return this;
    }

    /**
     * @return the verbose
     */
    public boolean isVerbose()
    {
        return verbose;
    }

    /**
     * @param verbose the verbose to set
     * @return The SchemaTool instance
     */
    public SchemaTool setVerbose(boolean verbose)
    {
        this.verbose = verbose;
        return this;
    }

    /**
     * Accessor for the DDL filename
     * @return the file to use when outputing the DDL
     */
    public String getDdlFile()
    {
        return ddlFilename;
    }

    /**
     * Mutator for the DDL file
     * @param file the file to use when outputting the DDL
     * @return The SchemaTool instance
     */
    public SchemaTool setDdlFile(String file)
    {
        this.ddlFilename = file;
        return this;
    }

    /**
     * Mutator for the flag to output complete DDL (when using DDL file)
     * @param completeDdl Whether to return complete DDL
     * @return The SchemaTool instance
     */
    public SchemaTool setCompleteDdl(boolean completeDdl)
    {
        this.completeDdl = completeDdl;
        return this;
    }

    /**
     * @return whether to use generate DDL (or just update DDL)
     */
    public boolean getCompleteDdl()
    {
        return completeDdl;
    }

    /**
     * Mutator for the flag to include auto-start mechanisms in schema updates
     * @param include Whether to include the auto-start mechanism specified by persistence props
     * @return The SchemaTool instance
     */
    public SchemaTool setIncludeAutoStart(boolean include)
    {
        this.includeAutoStart = include;
        return this;
    }

    /**
     * @return whether to include auto-start mechanisms in schema updates
     */
    public boolean getIncludeAutoStart()
    {
        return includeAutoStart;
    }
}