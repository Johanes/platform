/**********************************************************************
Copyright (c) 2008 Erik Bengtson and others. All rights reserved.
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
2008 Andy Jefferson - merged Compiler to make more understandable and compact
2008 Andy Jefferson - added compile() method with return object
    ...
 **********************************************************************/
package org.datanucleus.query.compiler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.query.JDOQLQueryHelper;
import org.datanucleus.query.compiler.JDOQLParser;
import org.datanucleus.query.expression.DyadicExpression;
import org.datanucleus.query.expression.Expression;
import org.datanucleus.query.expression.InvokeExpression;
import org.datanucleus.query.expression.Literal;
import org.datanucleus.query.expression.OrderExpression;
import org.datanucleus.query.expression.ParameterExpression;
import org.datanucleus.query.expression.PrimaryExpression;
import org.datanucleus.query.expression.VariableExpression;
import org.datanucleus.query.symbol.PropertySymbol;
import org.datanucleus.query.symbol.Symbol;
import org.datanucleus.query.symbol.SymbolResolver;
import org.datanucleus.query.symbol.SymbolTable;
import org.datanucleus.util.Imports;

/**
 * Implementation of a compiler for JDOQL (JSR0012, JSR0243).
 */
public class JDOQLCompiler extends JavaQueryCompiler implements SymbolResolver
{
    public JDOQLCompiler(MetaDataManager metaDataManager, ClassLoaderResolver clr, 
            String from, Class candidateClass, Collection candidates, 
            String filter, Imports imports, String ordering, String result, String grouping, String having, 
            String params, String variables, String update)
    {
        super(metaDataManager, clr, from, candidateClass, candidates, 
            filter, imports, ordering, result, grouping, having, params, variables, update);
    }

    /**
     * Method to compile the query, and return the compiled results.
     * @param parameters the parameter map of values keyed by param name
     * @param subqueryMap Map of subquery variables, keyed by the subquery name
     * @return The compiled query
     */
    public QueryCompilation compile(Map parameters, Map subqueryMap)
    {
        Map parseOptions = new HashMap();
        if (this.parameters != null)
        {
            parseOptions.put("explicitParameters", true);
        }
        else
        {
            parseOptions.put("implicitParameters", true);
        }
        parser = new JDOQLParser(parseOptions);
        symtbl = new SymbolTable(clr);
        symtbl.setSymbolResolver(this);
        if (parentCompiler != null)
        {
            symtbl.setParentSymbolTable(parentCompiler.symtbl);
        }

        if (subqueryMap != null && !subqueryMap.isEmpty())
        {
            // Load subqueries into symbol table so the compilation knows about them
            Iterator<String> subqueryIter = subqueryMap.keySet().iterator();
            while (subqueryIter.hasNext())
            {
                String subqueryName = subqueryIter.next();
                Symbol sym = new PropertySymbol(subqueryName);
                sym.setType(Symbol.VARIABLE);
                symtbl.addSymbol(sym);
            }
        }

        Expression[] exprFrom = compileFrom();
        compileCandidatesParametersVariables(parameters);
        Expression exprFilter = compileFilter();
        Expression[] exprOrdering = compileOrdering();
        Expression[] exprResult = compileResult();
        Expression[] exprGrouping = compileGrouping();
        Expression exprHaving = compileHaving();
        Expression[] exprUpdate = compileUpdate();

        // Impose checks from JDO spec
        if (exprGrouping != null)
        {
            // JDO spec 14.6.10. 
            // When grouping is specified, each result expression must be one of:
            // - an expression contained in the grouping expression; or,
            // - an aggregate expression evaluated once per group.
            if (exprResult != null)
            {
                for (int i=0;i<exprResult.length;i++)
                {
                    if (!isExpressionGroupingOrAggregate(exprResult[i], exprGrouping))
                    {
                        throw new NucleusUserException("JDOQL query has result clause " + exprResult[i] + 
                                " but this is invalid (see JDO spec 14.6.10)." +
                                " When specified with grouping should be aggregate, or grouping expression");
                    }
                }
            }

            // JDO spec 14.6.10 
            // When grouping is specified with ordering, each ordering expression must be one of:
            // - an expression contained in the grouping expression; or,
            // - an aggregate expression evaluated once per group.
            if (exprOrdering != null)
            {
                for (int i=0;i<exprOrdering.length;i++)
                {
                    if (!isExpressionGroupingOrAggregate(exprOrdering[i], exprGrouping))
                    {
                        throw new NucleusUserException("JDOQL query has ordering clause " + exprOrdering[i] + 
                                " but this is invalid (see JDO spec 14.6.10)." +
                                " When specified with grouping should be aggregate, or grouping expression");
                    }
                }
            }
        }
        if (exprHaving != null)
        {
            // JDO spec 14.6.10.
            // When "having" is specified, the "having" expression consists of arithmetic and boolean
            // expressions containing expressions that are either aggregate expressions or contained in a
            // grouping expression.
            if (!containsOnlyGroupingOrAggregates(exprHaving, exprGrouping))
            {
                throw new NucleusUserException("JDOQL query has having clause " + exprHaving + 
                    " but this is invalid (see JDO spec 14.6.10)." +
                    " Should contain only aggregates, or grouping expressions");
            }
        }

        QueryCompilation compilation = new QueryCompilation(candidateClass, candidateAlias, symtbl,
            exprResult, exprFrom, exprFilter, exprGrouping, exprHaving, exprOrdering, exprUpdate);
        compilation.setQueryLanguage(getLanguage());

        // Apply compilation optimisations
        boolean optimise = metaDataManager.getNucleusContext().getPersistenceConfiguration().getBooleanProperty(
            "datanucleus.query.compileOptimised");
        if (optimise)
        {
            QueryCompilerOptimiser optimiser = new QueryCompilerOptimiser(compilation);
            optimiser.optimise();
        }

        return compilation;
    }

    /**
     * Convenience method to check the provided expression for whether it contains only grouping expressions
     * or aggregates
     * @param expr The expression to check
     * @param exprGrouping The grouping expressions
     * @return Whether it contains only grouping or aggregates
     */
    private static boolean containsOnlyGroupingOrAggregates(Expression expr, Expression[] exprGrouping)
    {
        if (expr == null)
        {
            return true;
        }
        else if (expr instanceof DyadicExpression)
        {
            Expression left = expr.getLeft();
            Expression right = expr.getRight();
            if (!containsOnlyGroupingOrAggregates(left, exprGrouping))
            {
                return false;
            }
            if (!containsOnlyGroupingOrAggregates(right, exprGrouping))
            {
                return false;
            }
            return true;
        }
        else if (expr instanceof InvokeExpression)
        {
            InvokeExpression invExpr = (InvokeExpression)expr;
            if (isExpressionGroupingOrAggregate(invExpr, exprGrouping))
            {
                return true;
            }
            Expression invokedExpr = invExpr.getLeft();
            if (invokedExpr != null && !containsOnlyGroupingOrAggregates(invokedExpr, exprGrouping))
            {
                // Check invoked object
                return false;
            }
            List<Expression> invArgs = invExpr.getArguments();
            if (invArgs != null)
            {
                // Check invocation arguments
                Iterator<Expression> iter= invArgs.iterator();
                while (iter.hasNext())
                {
                    Expression argExpr = iter.next();
                    if (!containsOnlyGroupingOrAggregates(argExpr, exprGrouping))
                    {
                        return false;
                    }
                }
            }
            return true;
        }
        else if (expr instanceof PrimaryExpression)
        {
            return isExpressionGroupingOrAggregate(expr, exprGrouping);
        }
        else if (expr instanceof Literal)
        {
            return true;
        }
        else if (expr instanceof ParameterExpression)
        {
            return true;
        }
        else if (expr instanceof VariableExpression)
        {
            return true;
        }
        return false;
    }

    /**
     * Convenience method to check of the provided expression is either an aggregate expression or
     * is a grouping expression (or literal, parameter, or variable).
     * @param expr The expression to check
     * @param exprGrouping The grouping expressions
     * @return Whether it passes the test
     */
    private static boolean isExpressionGroupingOrAggregate(Expression expr, Expression[] exprGrouping)
    {
        if (expr instanceof InvokeExpression)
        {
            InvokeExpression invExpr = (InvokeExpression)expr;
            if (invExpr.getLeft() == null)
            {
                // Aggregate method
                String methodName = invExpr.getOperation();
                if (methodName.equals("avg") || methodName.equals("AVG") ||
                    methodName.equals("count") || methodName.equals("COUNT") ||
                    methodName.equals("sum") || methodName.equals("SUM") ||
                    methodName.equals("min") || methodName.equals("MIN") ||
                    methodName.equals("max") || methodName.equals("MAX"))
                {
                    return true;
                }
            }

            for (int j=0;j<exprGrouping.length;j++)
            {
                if (exprGrouping[j] instanceof InvokeExpression)
                {
                    if (invExpr.toString().equalsIgnoreCase(exprGrouping[j].toString()))
                    {
                        // e.g. 'bestFried.birthDate.getMonth()' is exactly the same as an expression in grouping
                        return true;
                    }
                }
            }
        }
        else if (expr instanceof PrimaryExpression)
        {
            PrimaryExpression primExpr = (PrimaryExpression)expr;
            String id = primExpr.getId();
            if (id.equals("this"))
            {
                return true;
            }
            for (int j=0;j<exprGrouping.length;j++)
            {
                if (exprGrouping[j] instanceof PrimaryExpression)
                {
                    String groupId = ((PrimaryExpression)exprGrouping[j]).getId();
                    if (id.equals(groupId))
                    {
                        return true;
                    }
                }
            }
        }
        else if (expr instanceof OrderExpression)
        {
            Expression orderExpr = ((OrderExpression)expr).getLeft();
            return isExpressionGroupingOrAggregate(orderExpr, exprGrouping);
        }
        else if (expr instanceof Literal)
        {
            return true;
        }
        else if (expr instanceof ParameterExpression)
        {
            return true;
        }
        else if (expr instanceof VariableExpression)
        {
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.symbol.SymbolResolver#supportsVariables()
     */
    public boolean supportsImplicitVariables()
    {
        if (variables != null)
        {
            // Query uses explicit variables, so don't allow implicit
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.symbol.SymbolResolver#caseSensitiveSymbolNames()
     */
    public boolean caseSensitiveSymbolNames()
    {
        return true;
    }

    /**
     * Accessor for the query language name.
     * @return Name of the query language.
     */
    public String getLanguage()
    {
        return "JDOQL";
    }

    /**
     * Method to return if the supplied name is a keyword.
     * Keywords can only appear at particular places in a query so we need to detect for valid queries.
     * @param name The name
     * @return Whether it is a keyword
     */
    protected boolean isKeyword(String name)
    {
        if (name == null)
        {
            return false;
        }
        else if (JDOQLQueryHelper.isKeyword(name))
        {
            return true;
        }
        /*else if (name.equals("this"))
        {
            return true;
        }*/
        return false;
    }
}