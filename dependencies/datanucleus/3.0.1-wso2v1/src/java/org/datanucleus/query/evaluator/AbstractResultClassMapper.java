/**********************************************************************
Copyright (c) 2007 Marcel Wirth and others. All rights reserved.
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
2008 Andy Jefferson - reworked to make extensive reuse of QueryUtils
    ...
 **********************************************************************/
package org.datanucleus.query.evaluator;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.query.QueryUtils;
import org.datanucleus.query.expression.Expression;
import org.datanucleus.query.expression.ParameterExpression;
import org.datanucleus.query.expression.PrimaryExpression;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * Abstract mapper for getting results from a query. Extend for the specific query language.
 */
public class AbstractResultClassMapper
{
    protected static final Localiser LOCALISER = Localiser.getInstance(
            "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    protected Class resultClass;

    public AbstractResultClassMapper(Class resultClass)
    {
        this.resultClass = resultClass;
    }

    /**
     * Method to map the input results to the required result class type.
     * @param inputResults The results to process
     * @param resultNames Expressions for the result components of the input results (columns)
     * @return Collection<resultClass>
     */
    public Collection map(final Collection inputResults, final Expression[] resultNames)
    {
        // Do as PrivilegedAction since uses reflection
        return (Collection) AccessController.doPrivileged(new PrivilegedAction()
        {
            public Object run()
            {
                String[] fieldNames = new String[resultNames.length];
                Field[] fields = new Field[fieldNames.length];
                for (int i=0;i<fieldNames.length;i++)
                {
                    if (resultNames[i] instanceof PrimaryExpression)
                    {
                        fieldNames[i] = ((PrimaryExpression)resultNames[i]).getId();
                        fields[i] = getFieldForFieldNameInResultClass(resultClass, fieldNames[i]);
                    }
                    else if (resultNames[i] instanceof ParameterExpression)
                    {
                        // TODO We need to cater for outputting the parameter value. Need SymbolTable
                        // This code below is wrong.
                        fieldNames[i] = ((ParameterExpression)resultNames[i]).getId();
                        fields[i] = getFieldForFieldNameInResultClass(resultClass, fieldNames[i]);
                    }
                    else
                    {
                        fieldNames[i] = resultNames[i].getAlias();
                        fields[i] = null;
                    }
                }
    
                List outputResults = new ArrayList();
                Iterator it = inputResults.iterator();
                while (it.hasNext())
                {
                    Object inputResult = it.next();
                    Object row = getResultForResultSetRow(inputResult, fieldNames, fields);
                    outputResults.add(row);
                }
                return outputResults;
            }
        });
    }

    /**
     * Method to take the result(s) of a row of the query and convert it into an object of the resultClass
     * type, using the rules from the JDO spec.
     * @param inputResult The result from the query
     * @param fieldNames Names of the fields (in the query, ordered)
     * @param fields The Field objects for the fields of the result class (ordered)
     * @return Object of the resultClass type for the input result
     */
    Object getResultForResultSetRow(Object inputResult, String[] fieldNames, Field[] fields)
    {
        if (resultClass == Object[].class)
        {
            return inputResult;
        }
        else if (QueryUtils.resultClassIsSimple(resultClass.getName()))
        {
            // User wants a single field
            if (fieldNames.length == 1)
            {
                if (inputResult == null || resultClass.isAssignableFrom(inputResult.getClass()))
                {
                    return inputResult;
                }
                else
                {
                    String msg = LOCALISER.msg("021202",
                        resultClass.getName(), inputResult.getClass().getName());
                    NucleusLogger.QUERY.error(msg);
                    throw new NucleusUserException(msg);
                }
            }
            else if (fieldNames.length > 1)
            {
                String msg = LOCALISER.msg("021201", resultClass.getName());
                NucleusLogger.QUERY.error(msg);
                throw new NucleusUserException(msg);
            }
            else
            {
                // 0 columns in the query ?
                return null;
            }
        }
        else
        {
            Object[] fieldValues = null;
            if (inputResult instanceof Object[])
            {
                fieldValues = (Object[])inputResult;
            }
            else
            {
                fieldValues = new Object[1];
                fieldValues[0] = inputResult;
            }
            Object obj = QueryUtils.createResultObjectUsingArgumentedConstructor(resultClass, fieldValues, null);
            if (obj != null)
            {
                return obj;
            }
            else if (NucleusLogger.QUERY.isDebugEnabled())
            {
                // Give debug message that no constructor was found with the right args
                Class[] ctr_arg_types = new Class[fieldNames.length];
                for (int i=0;i<fieldNames.length;i++)
                {
                    if (fieldValues[i] != null)
                    {
                        ctr_arg_types[i] = fieldValues[i].getClass();
                    }
                    else
                    {
                        ctr_arg_types[i] = null;
                    }
                }
                NucleusLogger.QUERY.debug(LOCALISER.msg("021206",
                    resultClass.getName(), StringUtils.objectArrayToString(ctr_arg_types)));
            }
    
            // B. No argumented constructor so create object and update fields using fields/put()/setXXX()
            return QueryUtils.createResultObjectUsingDefaultConstructorAndSetters(resultClass, fieldNames, 
                fields, fieldValues);
        }
    }

    /**
     * Accessor for the Field for the specified field name of the supplied class.
     * Caters for the field being in superclasses.
     * @param cls The class
     * @param fieldName Name of the field
     * @return The field
     */
    Field getFieldForFieldNameInResultClass(Class cls, String fieldName)
    {
        try
        {
            return cls.getDeclaredField(fieldName);
        }
        catch (NoSuchFieldException nsfe)
        {
            if (cls.getSuperclass() != null)
            {
                return getFieldForFieldNameInResultClass(cls.getSuperclass(), fieldName);
            }
        }
        return null;
    }

}