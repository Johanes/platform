/**********************************************************************
Copyright (c) 2008 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.query.evaluator.memory;

import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.query.QueryUtils;
import org.datanucleus.query.expression.InvokeExpression;
import org.datanucleus.query.expression.Literal;
import org.datanucleus.query.expression.ParameterExpression;
import org.datanucleus.query.expression.PrimaryExpression;
import org.datanucleus.util.Localiser;

/**
 * Evaluator for the method "{stringExpr}.indexOf(strExpr [,numExpr])".
 */
public class StringIndexOfMethodEvaluator implements InvocationEvaluator
{
    /** Localisation utility for output messages */
    protected static final Localiser LOCALISER = Localiser.getInstance("org.datanucleus.Localisation",
        org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /* (non-Javadoc)
     * @see org.datanucleus.query.evaluator.memory.InvocationEvaluator#evaluate(org.datanucleus.query.expression.InvokeExpression, org.datanucleus.query.evaluator.memory.InMemoryExpressionEvaluator)
     */
    public Object evaluate(InvokeExpression expr, Object invokedValue, InMemoryExpressionEvaluator eval)
    {
        String method = expr.getOperation();

        if (invokedValue == null)
        {
            return Integer.valueOf(-1);
        }
        if (!(invokedValue instanceof String))
        {
            throw new NucleusException(LOCALISER.msg("021011", method, invokedValue.getClass().getName()));
        }

        String arg1 = null;

        Object arg1Obj = null;
        Object param = expr.getArguments().get(0);
        if (param instanceof PrimaryExpression)
        {
            PrimaryExpression primExpr = (PrimaryExpression)param;
            arg1Obj = eval.getValueForPrimaryExpression(primExpr);
        }
        else if (param instanceof ParameterExpression)
        {
            ParameterExpression paramExpr = (ParameterExpression)param;
            arg1Obj = QueryUtils.getValueForParameterExpression(eval.getParameterValues(), paramExpr);
        }
        else if (param instanceof Literal)
        {
            arg1Obj = ((Literal)param).getLiteral();
        }
        else if (param instanceof InvokeExpression)
        {
            arg1Obj = eval.getValueForInvokeExpression((InvokeExpression) param);
        }
        else
        {
            throw new NucleusException(method + "(param[, num1]) where param is instanceof " + param.getClass().getName() + " not supported");
        }
        arg1 = QueryUtils.getStringValue(arg1Obj);

        Integer result = null;
        if (expr.getArguments().size() == 2)
        {
            param = expr.getArguments().get(1);
            int arg2 = -1;
            if (param instanceof Literal)
            {
                Literal param2 = (Literal)expr.getArguments().get(1);
                if (param2.getLiteral() instanceof Number)
                {
                    arg2 = ((Number)param2.getLiteral()).intValue();
                }
                else
                {
                    throw new NucleusException(method + "(param1, param2) : param2 must be numeric!");
                }
            }
            else
            {
                throw new NucleusException(method + "(param1, param2) where param2 is instanceof " + param.getClass().getName() + " not supported");
            }
            result = Integer.valueOf(((String)invokedValue).indexOf(arg1, arg2));
        }
        else
        {
            result = Integer.valueOf(((String)invokedValue).indexOf(arg1));
        }
        return result;
    }
}