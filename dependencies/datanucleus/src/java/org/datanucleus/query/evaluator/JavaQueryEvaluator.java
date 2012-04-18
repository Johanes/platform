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
2008 Andy Jefferson - restructured to take in applyXXX flags and evaluate different parts if required
    ...
**********************************************************************/
package org.datanucleus.query.evaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.query.QueryUtils;
import org.datanucleus.query.compiler.QueryCompilation;
import org.datanucleus.query.evaluator.memory.InMemoryExpressionEvaluator;
import org.datanucleus.query.evaluator.memory.InMemoryFailure;
import org.datanucleus.query.evaluator.memory.VariableNotSetException;
import org.datanucleus.query.expression.CreatorExpression;
import org.datanucleus.query.expression.Expression;
import org.datanucleus.query.expression.InvokeExpression;
import org.datanucleus.query.expression.OrderExpression;
import org.datanucleus.store.query.Query;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * Class to evaluate a Java "string-based" query in whole or part.
 * Takes in a list of instances and applies the required restrictions to the input giving a final result.
 * Typically extended for JDOQL, JPQL, SQL specifics.
 */
public abstract class JavaQueryEvaluator
{
    /** Localisation utility for output messages */
    protected static final Localiser LOCALISER = Localiser.getInstance(
        "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /** Name under which any set of results are stored in the state map. Used for aggregation. */
    public static final String RESULTS_SET = "DATANUCLEUS_RESULTS_SET";

    protected final String language;

    protected String candidateAlias = "this";

    /** Candidates objects to evaluate. */
    protected Collection candidates;

    /** Underlying "string-based" query. */
    protected Query query;

    /** Compilation of the underlying query, that we are evaluating. */
    protected QueryCompilation compilation;

    /** Map of input parameter values, keyed by the parameter name. */
    protected Map parameterValues;

    /** The evaluator. */
    protected InMemoryExpressionEvaluator evaluator;

    /** Map of state symbols for the query evaluation. */
    protected Map<String, Object> state;

    protected ClassLoaderResolver clr;

    /**
     * Constructor for the evaluator of a query in the specified language.
     * @param language Name of the language
     * @param query The underlying query
     * @param compilation Query compilation
     * @param parameterValues Input parameter values
     * @param clr ClassLoader resolver
     * @param candidates Candidate objects
     */
    public JavaQueryEvaluator(String language, Query query, QueryCompilation compilation, 
            Map parameterValues, ClassLoaderResolver clr, Collection candidates)
    {
        this.language = language;
        this.query = query;
        this.compilation = compilation;
        this.parameterValues = parameterValues;
        this.clr = clr;
        this.candidates = candidates;
        this.candidateAlias = (compilation.getCandidateAlias() != null ? 
                compilation.getCandidateAlias() : this.candidateAlias);

        state = new HashMap<String, Object>();
        state.put(this.candidateAlias, query.getCandidateClass());

        evaluator = new InMemoryExpressionEvaluator(query.getExecutionContext(),
            parameterValues, state, query.getParsedImports(), clr, this.candidateAlias);
    }

    /**
     * Method to evaluate a subquery of the query being evaluated.
     * @param subquery The subquery
     * @param compilation The subquery compilation
     * @param candidates The candidates for the subquery
     * @param outerCandidate The current outer candidate (for use when linking back to outer query)
     * @return The result
     */
    protected abstract Collection evaluateSubquery(Query subquery, QueryCompilation compilation, Collection candidates,
            Object outerCandidate);

    /**
     * Method to perform the evaluation, applying the query restrictions that are required.
     * @param applyFilter Whether to apply any filter constraints on the results
     * @param applyOrdering Whether to apply any order constraints on the results
     * @param applyResult Whether to apply any result/grouping/having on the results
     * @param applyResultClass Whether to apply any resultClass constraint on the results
     * @param applyRange Whether to apply any range constraint on the results
     * @return The results after evaluation.
     */
    public Collection execute(boolean applyFilter, boolean applyOrdering, boolean applyResult,
            boolean applyResultClass, boolean applyRange)
    {
        if (candidates.isEmpty())
        {
            // No candidates so all match the query!
            return candidates;
        }
        else if (!applyFilter && !applyOrdering && !applyResult && !applyResultClass && !applyRange)
        {
            // Nothing to evaluate in-memory
            return candidates;
        }

        Collection executeCandidates = null;
        Expression[] result = compilation.getExprResult();
        if (applyResult && result != null && result.length > 1)
        {
            // Have result but not returning rows of candidate type so remove dupd candidates
            executeCandidates = new ArrayList();
            Iterator candIter = candidates.iterator();
            while (candIter.hasNext())
            {
                Object candidate = candIter.next();
                if (!executeCandidates.contains(candidate))
                {
                    executeCandidates.add(candidate);
                }
            }
        }
        else
        {
            executeCandidates = candidates;
        }

        // TODO Where the subquery makes use of the parent query candidate, set the candidates for the
        // subquery using that. This currently just passes the same parent candidates in!
        String[] subqueryAliases = compilation.getSubqueryAliases();
        if (subqueryAliases != null)
        {
            for (int i=0;i<subqueryAliases.length;i++)
            {
                // Evaluate subquery first
                Query subquery = query.getSubqueryForVariable(subqueryAliases[i]).getQuery();
                QueryCompilation subqueryCompilation =
                    compilation.getCompilationForSubquery(subqueryAliases[i]);
                if (subqueryCompilation.getExprFrom() != null)
                {
                    // TODO Evaluate "from"
                    NucleusLogger.QUERY.warn("In-memory evaluation of subquery with 'from'=" + 
                        StringUtils.objectArrayToString(subqueryCompilation.getExprFrom()) +
                        " but from clause evaluation not currently supported!");
                }
                Collection subqueryResult = evaluateSubquery(subquery, subqueryCompilation, executeCandidates, null);

                if (QueryUtils.queryReturnsSingleRow(subquery))
                {
                    // Subquery is expected to return single value
                    state.put(subqueryAliases[i], subqueryResult.iterator().next());
                }
                else
                {
                    state.put(subqueryAliases[i], subqueryResult);
                }
            }
        }

        List resultSet = new ArrayList(executeCandidates);
        Expression filter = compilation.getExprFilter();
        if (applyFilter && filter != null)
        {
            // Process any filter constraints
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021012", "filter", language, filter));
            }
            resultSet = handleFilter(resultSet);
        }

        Expression[] ordering = compilation.getExprOrdering();
        if (applyOrdering && ordering != null)
        {
            // Process any ordering constraints
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021012", "ordering", language, 
                    StringUtils.objectArrayToString(ordering)));
            }
            resultSet = ordering(resultSet);
        }

        if (applyRange && query.getRange() != null)
        {
            // Process any range constraints
            long fromIncl = query.getRangeFromIncl();
            long toExcl = query.getRangeToExcl();
            if (query.getRangeFromInclParam() != null)
            {
                fromIncl = ((Number)parameterValues.get(query.getRangeFromInclParam())).longValue();
            }
            if (query.getRangeToExclParam() != null)
            {
                toExcl = ((Number)parameterValues.get(query.getRangeToExclParam())).longValue();
            }
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021012", "range", language, "" + fromIncl + "," + toExcl));
            }
            resultSet = handleRange(resultSet, fromIncl, toExcl);
        }

        if (applyResult && result != null)
        {
            // Process any result/grouping/having constraints
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021012", "result", language,
                    StringUtils.objectArrayToString(result)));
            }

            // Apply grouping
            List aggregateList = new ArrayList();
            List s = resultSet;
            Expression[] grouping = compilation.getExprGrouping();
            if (grouping != null)
            {
                s = sortByGrouping(resultSet);
            }

            aggregateList = s;
            if (grouping != null)
            {
                aggregateList = handleAggregates(s);
            }

            resultSet = handleResult(aggregateList);

            if (query.getResultDistinct())
            {
                List tmpList = new ArrayList();
                Iterator iter = resultSet.iterator();
                while (iter.hasNext())
                {
                    Object obj = iter.next();
                    if (!tmpList.contains(obj)) // Omit dups
                    {
                        tmpList.add(obj);
                    }
                }
                resultSet = tmpList;
            }
        }

        if (applyResultClass && query.getResultClass() != null)
        {
            // Process any result class constraints
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021012", "resultClass", language, query.getResultClass().getName()));
            }
            if (!(result[0] instanceof CreatorExpression))
            {
                return this.mapResultClass(resultSet);
            }
        }

        return resultSet;
    }

    private List handleFilter(List set)
    {
        Expression filter = compilation.getExprFilter();
        if (filter == null)
        {
            return set;
        }
    
        List result = new ArrayList();
        Iterator it = set.iterator();
        if (NucleusLogger.QUERY.isDebugEnabled())
        {
            NucleusLogger.QUERY.debug("Evaluating filter for " + set.size() + " candidates");
        }
        while (it.hasNext())
        {
            // Set the value of the candidate being tested, and evaluate it
            Object obj = it.next();
            if (!state.containsKey(candidateAlias))
            {
                throw new NucleusUserException("Alias \"" + candidateAlias + "\" doesn't exist in the query or the candidate alias wasn't defined");
            }
            state.put(candidateAlias, obj);

            InMemoryExpressionEvaluator eval =
                new InMemoryExpressionEvaluator(query.getExecutionContext(), 
                    parameterValues, state, query.getParsedImports(), clr, candidateAlias);

            Object evalResult = evaluateBooleanExpression(filter, eval);
            if (Boolean.TRUE.equals(evalResult))
            {
                if (NucleusLogger.QUERY.isDebugEnabled())
                {
                    NucleusLogger.QUERY.debug(LOCALISER.msg("021023", StringUtils.toJVMIDString(obj)));
                }
                result.add(obj);
            }
        }
        return result;
    }

    /**
     * Convenience method to evaluate the provided expression returning a boolean.
     * Caters for variables in the provided expression, attempting to process all possible values for them.
     * @param expr The expression
     * @param eval The evaluator
     * @return The result
     */
    private Boolean evaluateBooleanExpression(Expression expr, InMemoryExpressionEvaluator eval)
    {
        try
        {
            Object result = expr.evaluate(eval);
            return ((result instanceof InMemoryFailure) ? Boolean.FALSE : (Boolean)result);
        }
        catch (VariableNotSetException vnse)
        {
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021024", vnse.getVariableExpression().getId(),
                    StringUtils.objectArrayToString(vnse.getValues())));
            }

            if (vnse.getValues() == null || vnse.getValues().length == 0)
            {
                // No possible values for the variable so fails
                return Boolean.FALSE;
            }

            // Set this variable and start iteration over the possible variable values
            for (int i=0;i<vnse.getValues().length;i++)
            {
                eval.setVariableValue(vnse.getVariableExpression().getId(), vnse.getValues()[i]);
                if (NucleusLogger.QUERY.isDebugEnabled())
                {
                    NucleusLogger.QUERY.debug(LOCALISER.msg("021025", vnse.getVariableExpression().getId(),
                        vnse.getValues()[i]));
                }
                if (Boolean.TRUE.equals(evaluateBooleanExpression(expr, eval)))
                {
                    return Boolean.TRUE;
                }
            }
            if (NucleusLogger.QUERY.isDebugEnabled())
            {
                NucleusLogger.QUERY.debug(LOCALISER.msg("021026", vnse.getVariableExpression().getId()));
            }
            eval.removeVariableValue(vnse.getVariableExpression().getId());

            return Boolean.FALSE;
        }
    }

    private List handleRange(List set, long fromIncl, long toExcl)
    {
        if (toExcl - fromIncl <= 0)
        {
            return Collections.EMPTY_LIST;
        }
        else
        {
            List resultList = new ArrayList();
            Iterator it = set.iterator();
            // skipping the unnecessary objects
            for (long l = 0; l < fromIncl && it.hasNext(); l++)
            {
                it.next();
            }
            long l = 0;
            while (l < (toExcl - fromIncl) && it.hasNext())
            {
                resultList.add(it.next());
                l++;
            }
            return resultList;
        }
    }

    private List sortByGrouping(List set)
    {
        Object[] o = set.toArray();
        final Expression[] grouping = compilation.getExprGrouping();
        Arrays.sort(o, new Comparator()
        {
            public int compare(Object arg0, Object arg1)
            {
                for (int i=0; i<grouping.length; i++)
                {
                    state.put(candidateAlias, arg0);
                    Object a = grouping[i].evaluate(evaluator);
                    state.put(candidateAlias, arg1);
                    Object b = grouping[i].evaluate(evaluator);
                    int result = ((Comparable)a).compareTo(b);
                    if (result != 0)
                    {
                        return result;
                    }
                }
                return 0;
            }
        });
        return Arrays.asList(o);
    }

    private List ordering(List set)
    {
        final Expression[] ordering = compilation.getExprOrdering();
        if (ordering == null)
        {
            return set;
        }

        // Save the result set
        state.put(RESULTS_SET, set);

        Object[] o = set.toArray();
        Arrays.sort(o, new Comparator()
        {
            public int compare(Object arg0, Object arg1)
            {
                for (int i=0; i<ordering.length; i++)
                {
                    state.put(candidateAlias, arg0);
                    Object a = ordering[i].evaluate(
                        new InMemoryExpressionEvaluator(query.getExecutionContext(), parameterValues, state, 
                            query.getParsedImports(), clr, candidateAlias));

                    state.put(candidateAlias, arg1);
                    Object b = ordering[i].evaluate(
                        new InMemoryExpressionEvaluator(query.getExecutionContext(), parameterValues, state,
                            query.getParsedImports(), clr, candidateAlias));

                    // Put any null values at the end
                    if (a == null && b == null)
                    {
                        return 0;
                    }
                    else if (a == null)
                    {
                        return -1;
                    }
                    else if (b == null)
                    {
                        return 1;
                    }

                    int result = ((Comparable)a).compareTo(b);
                    if (result != 0)
                    {
                        OrderExpression orderExpr = (OrderExpression)ordering[i];
                        if (orderExpr.getSortOrder() == null || orderExpr.getSortOrder().equals("ascending"))
                        {
                            // Ascending
                            return result;
                        }
                        else
                        {
                            // Descending
                            return -1 * result;
                        }
                    }
                }
                return 0;
            }
        });
        return Arrays.asList(o);
    }

    /**
     * Checks if there are aggregates and handle it.
     * @param resultSet The resultSet containing all elements
     * @return A list with aggregated elements
     */
    private List handleAggregates(List resultSet)
    {
        final Expression[] grouping = compilation.getExprGrouping();
        Comparator c = new Comparator()
        {
            public int compare(Object arg0, Object arg1)
            {
                for (int i=0; i<grouping.length; i++)
                {
                    state.put(candidateAlias, arg0);
                    Object a = grouping[i].evaluate(evaluator);
                    state.put(candidateAlias, arg1);
                    Object b = grouping[i].evaluate(evaluator);
                    int result = ((Comparable)a).compareTo(b);
                    if (result != 0)
                    {
                        return result;
                    }
                }
                return 0;
            }
        };
    
        List groups = new ArrayList();
        List group = new ArrayList();
        groups.add(group);
        for (int i=0; i<resultSet.size(); i++)
        {
            if (i > 0)
            {
                if (c.compare(resultSet.get(i-1),resultSet.get(i)) != 0)
                {
                    group = new ArrayList();
                    groups.add(group);
                }
            }
            group.add(resultSet.get(i));
        }
        List result = new ArrayList();
        Expression having = compilation.getExprHaving();
        if (having != null)
        {
            for (int i=0; i<groups.size(); i++)
            {
                if (satisfiesHavingClause((List) groups.get(i)))
                {
                    result.addAll((Collection) groups.get(i));
                }
            }
        }
        else
        {
            for (int i = 0; i < groups.size(); i++)
            {
                result.addAll((Collection) groups.get(i));
            }
        }
        return result;
    }

    /**
     * Checks if the results set fulfils the having clause.
     * @param set Set of results
     * @return true if fulfilling having clause
     */
    private boolean satisfiesHavingClause(List set)
    {
        state.put(RESULTS_SET, set);

        Expression having = compilation.getExprHaving();
        if (having.evaluate(evaluator) == Boolean.TRUE)
        {
            return true;
        }
        return false;
    }

    /**
     * Checks if there are aggregates and handles it.
     * @param resultSet The resultSet containing all elements
     * @return A list with aggregated elements
     */
    private List handleResult(List resultSet)
    {
        List result = new ArrayList();
        final Expression[] grouping = compilation.getExprGrouping();
        if (grouping != null)
        {
            Comparator c = new Comparator()
            {
                public int compare(Object arg0, Object arg1)
                {
                    if (grouping != null)
                    {
                        for (int i = 0; i < grouping.length; i++)
                        {
                            state.put(candidateAlias, arg0);
                            Object a = grouping[i].evaluate(evaluator);
                            state.put(candidateAlias, arg1);
                            Object b = grouping[i].evaluate(evaluator);
                            int result = ((Comparable) a).compareTo(b);
                            if (result != 0)
                            {
                                return result;
                            }
                        }
                    }
                    return 0;
                }
            };
    
            List groups = new ArrayList();
            List group = new ArrayList();
            if (resultSet.size() > 0)
            {
                groups.add(group);
            }
            for (int i = 0; i < resultSet.size(); i++)
            {
                if (i > 0)
                {
                    if (c.compare(resultSet.get(i - 1), resultSet.get(i)) != 0)
                    {
                        group = new ArrayList();
                        groups.add(group);
                    }
                }
                group.add(resultSet.get(i));
            }

            // Apply the result to the generated groups
            for (int i = 0; i < groups.size(); i++)
            {
                group = (List)groups.get(i);
                result.add(result(group));
            }
        }
        else
        {
            boolean aggregates = false;
            Expression[] resultExprs = compilation.getExprResult();
            if (resultExprs.length > 0 && resultExprs[0] instanceof CreatorExpression)
            {
                Expression[] resExpr = ((CreatorExpression)resultExprs[0]).getArguments().toArray(
                    new Expression[((CreatorExpression)resultExprs[0]).getArguments().size()]);
                for (int i = 0; i < resExpr.length; i++)
                {
                    if (resExpr[i] instanceof InvokeExpression)
                    {
                        String method = ((InvokeExpression) resExpr[i]).getOperation().toLowerCase();
                        if (method.equals("count") || method.equals("sum") || method.equals("avg") || 
                            method.equals("min") || method.equals("max"))
                        {
                            aggregates = true;
                        }
                    }
                }
            }
            else
            {
                for (int i = 0; i < resultExprs.length; i++)
                {
                    if (resultExprs[i] instanceof InvokeExpression)
                    {
                        String method = ((InvokeExpression)resultExprs[i]).getOperation().toLowerCase();
                        if (method.equals("count") || method.equals("sum") || method.equals("avg") || 
                            method.equals("min") || method.equals("max"))
                        {
                            aggregates = true;
                        }
                    }
                }
            }
    
            if (aggregates)
            {
                result.add(result(resultSet));
            }
            else
            {
                for (int i = 0; i < resultSet.size(); i++)
                {
                    result.add(result(resultSet.get(i)));
                }
            }
        }

        if (result.size() > 0 && ((Object[])result.get(0)).length == 1)
        {
            List r = result;
            result = new ArrayList();
            for (int i = 0; i < r.size(); i++)
            {
                result.add(((Object[]) r.get(i))[0]);
            }
        }
        return result;
    }

    private Object[] result(Object obj)
    {
        state.put(candidateAlias, obj);

        Expression[] result = compilation.getExprResult();
        Object[] r = new Object[result.length];
        for (int i=0; i<result.length; i++)
        {
            r[i] = result[i].evaluate(evaluator);
        }
    
        return r;
    }

    private Object[] result(List set)
    {
        // Store the results set so we can aggregate
        state.put(RESULTS_SET, set);

        Expression[] result = compilation.getExprResult();

        // Use first element only (if there are any). Should be same in other "group-by" components
        Object element = (set != null && set.size() > 0 ? set.get(0) : null);
        state.put(candidateAlias, element);

        Object[] r = new Object[result.length];
        for (int j=0; j<result.length; j++)
        {
            r[j] = result[j].evaluate(evaluator);
        }

        return r;
    }

    /**
     * Constructs ResultClassMapper and calls its map function
     * @param resultSet The resultSet containing the instances handled by setResult
     * @return The resultSet containing instances of the Class defined by setResultClass
     */
    abstract Collection mapResultClass(Collection resultSet);
}