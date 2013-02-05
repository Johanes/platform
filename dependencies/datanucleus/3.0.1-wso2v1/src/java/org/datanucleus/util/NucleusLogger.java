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
package org.datanucleus.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.datanucleus.exceptions.NucleusException;

/**
 * Logging framework for DataNucleus. Allows use of Log4J, JDK1.4, or no logging.
 * Performs a similar role to Apache CommonsLogging yet doesn't need an extra jar to be present in 
 * the CLASSPATH and also allows for no available logger.
 * Provides a series of predefined Loggers that can be used in the persistence process.
 * Also provides a method to create your own logger category.
 */
public abstract class NucleusLogger
{
    /** Implementation of NucleusLogger providing the logger. */
    private static Class LOGGER_CLASS = null;

    /** Log for Persistence issues */
    public static final NucleusLogger PERSISTENCE;

    /** Log for Lifecycle issues */
    public static final NucleusLogger LIFECYCLE;

    /** Log for Query issues */
    public static final NucleusLogger QUERY;

    /** Log for METADATA issues */
    public static final NucleusLogger METADATA;

    /** Log for MANAGEMENT issues */
    public static final NucleusLogger MANAGEMENT;

    /** Log for Cache issues */
    public static final NucleusLogger CACHE;

    /** Log for General issues */
    public static final NucleusLogger GENERAL;

    /** Log for Transaction issues */
    public static final NucleusLogger TRANSACTION;

    /** Log for Connection issues */
    public static final NucleusLogger CONNECTION;    

    /** Log for ClassLoading issues */
    public static final NucleusLogger CLASSLOADING;

    /** Log for PLUGIN issues */
    public static final NucleusLogger PLUGIN;

    /** Log for value generation issues */
    public static final NucleusLogger VALUEGENERATION;

    /** Log for Datastore issues */
    public static final NucleusLogger DATASTORE;

    /** Log for Datastore persistence issues */
    public static final NucleusLogger DATASTORE_PERSIST;

    /** Log for Datastore retrieval issues */
    public static final NucleusLogger DATASTORE_RETRIEVE;

    /** Log for Datastore Schema issues */
    public static final NucleusLogger DATASTORE_SCHEMA;

    /** Log for Datastore native operations */
    public static final NucleusLogger DATASTORE_NATIVE;

    static
    {
        // Set the log type to be used based on what is available from this ClassLoader
        // Note that we could have registered in the PluginManager but that needs to log too
        Class loggerClass = null;
        try
        {
            NucleusLogger.class.getClassLoader().loadClass("org.apache.log4j.Logger");
            loggerClass = org.datanucleus.util.Log4JLogger.class;
        }
        catch (Exception e)
        {
            loggerClass = org.datanucleus.util.JDK14Logger.class;
        }
        LOGGER_CLASS = loggerClass;

        // Create the Loggers for our predefined categories
        PERSISTENCE = getLoggerInstance("DataNucleus.Persistence");
        LIFECYCLE = getLoggerInstance("DataNucleus.Lifecycle");
        QUERY = getLoggerInstance("DataNucleus.Query");
        METADATA = getLoggerInstance("DataNucleus.MetaData");
        CACHE = getLoggerInstance("DataNucleus.Cache");
        GENERAL = getLoggerInstance("DataNucleus.General");
        TRANSACTION = getLoggerInstance("DataNucleus.Transaction");
        PLUGIN = getLoggerInstance("DataNucleus.Plugin");
        VALUEGENERATION = getLoggerInstance("DataNucleus.ValueGeneration");
        CLASSLOADING = getLoggerInstance("DataNucleus.ClassLoading");
        MANAGEMENT = getLoggerInstance("DataNucleus.Management");
        CONNECTION = getLoggerInstance("DataNucleus.Connection");
        DATASTORE = getLoggerInstance("DataNucleus.Datastore");
        DATASTORE_PERSIST = getLoggerInstance("DataNucleus.Datastore.Persist");
        DATASTORE_RETRIEVE = getLoggerInstance("DataNucleus.Datastore.Retrieve");
        DATASTORE_SCHEMA = getLoggerInstance("DataNucleus.Datastore.Schema");
        DATASTORE_NATIVE = getLoggerInstance("DataNucleus.Datastore.Native");
    }

    /**
     * Method to create a logger instance.
     * @param logCategory The category (or null)
     * @return The logger
     */
    public static NucleusLogger getLoggerInstance(String logCategory)
    {
        // Note that this uses reflection directly rather than ClassUtils since we don't want to cause
        // initialisation of any other class before we get our loggers installed
        Object obj;
        Class[] ctrTypes = new Class[] {String.class};
        Object[] ctrArgs = new Object[] {logCategory};
        try
        {
            Constructor ctor = LOGGER_CLASS.getConstructor(ctrTypes);
            obj = ctor.newInstance(ctrArgs);
        }
        catch (NoSuchMethodException e)
        {
            throw new NucleusException(
                "Missing constructor in class " + LOGGER_CLASS.getName() + 
                ", parameters " + Arrays.asList(ctrTypes).toString(), new Exception[]{e}).setFatal();
        }
        catch (IllegalAccessException e)
        {
            throw new NucleusException("Failed attempting to access class " + LOGGER_CLASS.getName(),
                new Exception[]{e}).setFatal();
        }
        catch (InstantiationException e)
        {
            throw new NucleusException("Failed instantiating a new object of type " + LOGGER_CLASS.getName(), 
                new Exception[]{e}).setFatal();
        }
        catch (InvocationTargetException e)
        {
            Throwable t = e.getTargetException();
            if (t instanceof RuntimeException)
            {
                throw (RuntimeException) t;
            }
            else if (t instanceof Error)
            {
                throw (Error) t;
            }
            else
            {
                throw new NucleusException("Unexpected exception thrown by constructor for " + LOGGER_CLASS.getName() + "," + t).setFatal();
            }
        }
        return (NucleusLogger)obj;
    }

    /**
     * Log a debug message.
     * @param msg The message
     */
    public abstract void debug(Object msg);

    /**
     * Log a debug message with throwable.
     * @param msg The message
     * @param thr A throwable
     */
    public abstract void debug(Object msg, Throwable thr);

    /**
     * Log an info message.
     * @param msg The message
     */
    public abstract void info(Object msg);

    /**
     * Log an info message with throwable.
     * @param msg The message
     * @param thr A throwable
     */
    public abstract void info(Object msg, Throwable thr);

    /**
     * Log a warning message.
     * @param msg The message
     */
    public abstract void warn(Object msg);

    /**
     * Log a warning message with throwable.
     * @param msg The message
     * @param thr A throwable
     */
    public abstract void warn(Object msg, Throwable thr);

    /**
     * Log an error message.
     * @param msg The message
     */
    public abstract void error(Object msg);

    /**
     * Log an error message with throwable.
     * @param msg The message
     * @param thr A throwable
     */
    public abstract void error(Object msg, Throwable thr);

    /**
     * Log a fatal message.
     * @param msg The message
     */
    public abstract void fatal(Object msg);

    /**
     * Log a fatal message with throwable.
     * @param msg The message
     * @param thr A throwable
     */
    public abstract void fatal(Object msg, Throwable thr);

    /**
     * Accessor for whether debug logging is enabled
     * @return Whether it is enabled
     */
    public abstract boolean isDebugEnabled();

    /**
     * Accessor for whether info logging is enabled
     * @return Whether it is enabled
     */
    public abstract boolean isInfoEnabled();
}