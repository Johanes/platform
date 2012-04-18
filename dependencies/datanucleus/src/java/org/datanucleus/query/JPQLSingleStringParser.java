/**********************************************************************
Copyright (c) 2006 Erik Bengtson and others. All rights reserved.
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
2008 Andy Jefferson - cater for subqueries, bulk update, bulk delete parsing
    ...
**********************************************************************/
package org.datanucleus.query;

import java.util.StringTokenizer;

import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.store.ExecutionContext;
import org.datanucleus.store.StoreManager;
import org.datanucleus.store.query.Query;
import org.datanucleus.util.ClassUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

/**
 * Parser for handling JPQL Single-String queries.
 * Takes a JPQLQuery and the query string and parses it into its constituent parts, updating
 * the JPQLQuery accordingly with the result that after calling the parse() method the JPQLQuery
 * is populated.
 * <pre>
 * SELECT [ {result} ]
 *        [FROM {candidate-classes} ]
 *        [WHERE {filter}]
 *        [GROUP BY {grouping-clause} ]
 *        [HAVING {having-clause} ]
 *        [ORDER BY {ordering-clause}]
 * e.g SELECT c FROM Customer c INNER JOIN c.orders o WHERE c.status = 1
 * </pre>
 * or
 * <pre>
 * UPDATE {update-clause}
 * WHERE {filter}
 * and update-clause is of the form
 * "Entity [[AS] identifier] SET {field = new_value}, ..."
 * </pre>
 * or
 * <pre>
 * DELETE {delete-clause}
 * WHERE {filter}
 * and delete-clause is of the form
 * "FROM Entity [[AS] identifier]"
 * </pre>
 * <p>
 * Note that {filter} and {having-clause} can contain subqueries, hence containing keywords
 * <pre>
 * SELECT c FROM Customer c WHERE NOT EXISTS (SELECT o1 FROM c.orders o1)
 * </pre>
 * So the "filter" for the outer query is "NOT EXISTS (SELECT o1 FROM c.orders o1)"
 */
public class JPQLSingleStringParser
{
    /** Localiser for messages. */
    protected static final Localiser LOCALISER = Localiser.getInstance(
        "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

    /** The JPQL query to populate. */
    private Query query;

    /** The single-string query string. */
    private String queryString;

    /**
     * Constructor for the Single-String parser.
     * @param query The query into which we populate the components of the query
     * @param queryString The Single-String query
     */
    public JPQLSingleStringParser(Query query, String queryString)
    {
        if (NucleusLogger.QUERY.isDebugEnabled())
        {
            NucleusLogger.QUERY.debug(LOCALISER.msg("043000", queryString));
        }
        this.query = query;
        this.queryString = queryString;
    }

    /**
     * Method to parse the Single-String query
     */
    public void parse()
    {
        new Compiler(new Parser(queryString)).compile();
    }

    /**
     * Compiler to process keywords contents. In the query the keywords often have
     * content values following them that represent the constituent parts of the query. This takes the keyword
     * and sets the constituent part accordingly.
     */
    private class Compiler
    {
        Parser tokenizer;
        
        Compiler(Parser tokenizer)
        {
            this.tokenizer = tokenizer;
        }

        private void compile()
        {
            compileQuery();

            // any keyword after compiling the SELECT is an error
            String keyword = tokenizer.parseKeyword();
            if (keyword != null)
            {
                if (JPQLQueryHelper.isKeyword(keyword))
                {
                    throw new NucleusUserException(LOCALISER.msg("043001", keyword));
                }
                else
                {
                    // unexpected token
                }
            }
        }

        private void compileQuery()
        {
            boolean update = false;
            boolean delete = false;
            if (tokenizer.parseKeywordIgnoreCase("SELECT"))
            {
                // Do nothing
            }
            else if (tokenizer.parseKeywordIgnoreCase("UPDATE"))
            {
                update = true;
                query.setType(Query.BULK_UPDATE);
            }
            else if (tokenizer.parseKeywordIgnoreCase("DELETE"))
            {
                delete = true;
                query.setType(Query.BULK_DELETE);
            }
            else
            {
                throw new NucleusUserException(LOCALISER.msg("043002"));
            }

            if (update)
            {
                compileUpdate();
            }
            else if (!delete)
            {
                compileResult();
            }

            if (tokenizer.parseKeywordIgnoreCase("FROM"))
            {
                compileFrom();
            }
            if (tokenizer.parseKeywordIgnoreCase("WHERE"))
            {
                compileWhere();
            }
            if (tokenizer.parseKeywordIgnoreCase("GROUP BY"))
            {
                if (update || delete)
                {
                    throw new NucleusUserException(LOCALISER.msg("043007"));
                }
                compileGroup();
            }
            if (tokenizer.parseKeywordIgnoreCase("HAVING"))
            {
                if (update || delete)
                {
                    throw new NucleusUserException(LOCALISER.msg("043008"));
                }
                compileHaving();
            }
            if (tokenizer.parseKeywordIgnoreCase("ORDER BY"))
            {
                if (update || delete)
                {
                    throw new NucleusUserException(LOCALISER.msg("043009"));
                }
                compileOrder();
            }
        }

        private void compileResult()
        {
            String content = tokenizer.parseContent(null, false);
            if (content.length() > 0)
            {
                //content may be empty
                query.setResult(content);
            }
        }

        private void compileUpdate()
        {
            String content = tokenizer.parseContent(null, false);
            if (content.length() == 0)
            {
                // No UPDATE clause
                throw new NucleusUserException(LOCALISER.msg("043010"));
            }

            String contentUpper = content.toUpperCase();
            int setIndex = contentUpper.indexOf("SET");
            if (setIndex < 0)
            {
                // UPDATE clause has no "SET ..." !
                throw new NucleusUserException(LOCALISER.msg("043011"));
            }
            query.setFrom(content.substring(0, setIndex).trim());
            query.setUpdate(content.substring(setIndex+3).trim());
        }

        private void compileFrom()
        {
            String content = tokenizer.parseContent(null, false);
            if (content.length() > 0)
            {
                //content may be empty
                query.setFrom(content);
            }
        }

        private void compileWhere()
        {
            // "TRIM" may include "FROM" keyword so ignore subsequent FROMs
            String content = tokenizer.parseContent("FROM", true);
            if (content.length() == 0)
            {
                // content cannot be empty
                throw new NucleusUserException(LOCALISER.msg("043004", "WHERE", "<filter>"));
            }

            String contentUpper = content.toUpperCase();
            if (contentUpper.indexOf("SELECT ") > 0) // Case insensitive search
            {
                // Subquery (or subqueries) present so split them out and just apply the filter for this query
                processFilterContent(content);
            }
            else
            {
                query.setFilter(content);
            }
        }

        /**
         * Method to extract the filter clause for this query, splitting out any subqueries and
         * replacing by variables in this filter, and adding to the query as actual subqueries.
         * @param content The input string
         */
        private void processFilterContent(String content)
        {
            StringBuilder stringContent = new StringBuilder();
            boolean withinLiteralDouble = false;
            boolean withinLiteralSingle = false;
            int subqueryNum = 1;
            for (int i=0;i<content.length();i++)
            {
                boolean subqueryProcessed = false;
                char chr = content.charAt(i);

                if (chr == '"')
                {
                    withinLiteralDouble = !withinLiteralDouble;
                }
                else if (chr == '\'')
                {
                    withinLiteralSingle = !withinLiteralSingle;
                }

                if (!withinLiteralDouble && !withinLiteralSingle)
                {
                    if (chr == '(')
                    {
                        // Check for SELECT/select form of subquery
                        String remains = content.substring(i+1).trim();
                        if (remains.toUpperCase().startsWith("SELECT"))
                        {
                            // subquery, so find closing brace and process it
                            remains = content.substring(i);
                            int endPosition = -1;
                            int braceLevel = 0;
                            for (int j=1;j<remains.length();j++) // Omit opening brace since we know about it
                            {
                                if (remains.charAt(j) == '(')
                                {
                                    braceLevel++;
                                }
                                else if (remains.charAt(j) == ')')
                                {
                                    braceLevel--;
                                    if (braceLevel < 0) // Closing brace for the subquery
                                    {
                                        endPosition = i+j;
                                        break;
                                    }
                                }
                            }
                            if (endPosition < 0)
                            {
                                throw new NucleusUserException(LOCALISER.msg("042017"));
                            }

                            String subqueryStr = content.substring(i+1, endPosition).trim();
                            String subqueryVarName = "DATANUCLEUS_SUBQUERY_" + subqueryNum;

                            Query subquery = (Query)ClassUtils.newInstance(query.getClass(),
                                new Class[]{StoreManager.class, ExecutionContext.class, String.class},
                                new Object[] {query.getStoreManager(), query.getExecutionContext(), subqueryStr});
                            // TODO Set the type of the variable
                            query.addSubquery(subquery, "double " + subqueryVarName, null, null);

                            stringContent.append(subqueryVarName);
                            i = endPosition;
                            subqueryNum++;
                            subqueryProcessed = true;
                        }
                    }
                }
                if (!subqueryProcessed)
                {
                    stringContent.append(chr);
                }
            }

            if (withinLiteralDouble || withinLiteralSingle)
            {
                // Literal wasn't closed
                throw new NucleusUserException(LOCALISER.msg("042017"));
            }

            query.setFilter(stringContent.toString());
        }

        private void compileGroup()
        {
            String content = tokenizer.parseContent(null, false);
            if (content.length() == 0)
            {
                // content cannot be empty
                throw new NucleusUserException(LOCALISER.msg("043004", "GROUP BY", "<grouping>"));
            }
            query.setGrouping(content);
        }

        private void compileHaving()
        {
            // "TRIM" may include "FROM" keyword so ignore subsequent FROMs
            String content = tokenizer.parseContent("FROM", true);
            if (content.length() == 0)
            {
                // content cannot be empty
                throw new NucleusUserException(LOCALISER.msg("043004", "HAVING", "<having>"));
            }
            query.setHaving(content);
        }

        private void compileOrder()
        {
            String content = tokenizer.parseContent(null, false);
            if (content.length() == 0)
            {
                // content cannot be empty
                throw new NucleusUserException(LOCALISER.msg("043004", "ORDER BY", "<ordering>"));
            }
            query.setOrdering(content);
        }
    }

    /**
     * Tokenizer that provides access to current token.
     */
    private static class Parser
    {
        final String queryString;

        int queryStringPos = 0;

        /** tokens */
        final String[] tokens;

        /** keywords */
        final String[] keywords;

        /** current token cursor position */
        int tokenIndex = -1;

        /**
         * Constructor
         * @param str Query string
         */
        public Parser(String str)
        {
            queryString = str;

            StringTokenizer tokenizer = new StringTokenizer(str);
            tokens = new String[tokenizer.countTokens()];
            keywords = new String[tokenizer.countTokens()];
            int i = 0;
            while (tokenizer.hasMoreTokens())
            {
                tokens[i++] = tokenizer.nextToken();
            }
            for (i = 0; i < tokens.length; i++)
            {
                if (JPQLQueryHelper.isKeyword(tokens[i]))
                {
                    keywords[i] = tokens[i];
                }
                else if (i < tokens.length - 1 && JPQLQueryHelper.isKeyword(tokens[i] + ' ' + tokens[i + 1]))
                {
                    keywords[i] = tokens[i];
                    i++;
                    keywords[i] = tokens[i];
                }
            }
        }

        /**
         * Parse the content until a keyword is found.
         * @param keywordToIgnore Ignore this keyword if found first
         * @param allowSubentries Whether to permit subentries (in parentheses) in this next block
         * @return the content
         */
        public String parseContent(String keywordToIgnore, boolean allowSubentries)
        {
            String content = "";
            int level = 0;

            while (tokenIndex < tokens.length - 1)
            {
                tokenIndex++;

                if (allowSubentries)
                {
                    // Process this token to check level of parentheses.
                    // This is necessary because we want to ignore keywords if within a parentheses-block
                    // e.g SELECT ... FROM ... WHERE ... EXISTS (SELECT FROM ...)
                    // and the "WHERE" part is "... EXISTS (SELECT FROM ...)"
                    // Consequently subqueries will be parsed into the relevant block correctly.
                    // Assumes that subqueries are placed in parentheses
                    for (int i=0;i<tokens[tokenIndex].length();i++)
                    {
                        char c = tokens[tokenIndex].charAt(i);
                        if (c == '(')
                        {
                            level++;
                        }
                        else if (c == ')')
                        {
                            level--;
                        }
                    }
                }

                if (level == 0 && JPQLQueryHelper.isKeyword(tokens[tokenIndex]) && !tokens[tokenIndex].equals(keywordToIgnore))
                {
                    // Invalid keyword encountered and not currently in subquery block
                    tokenIndex--;
                    break;
                }
                else if (level == 0 && tokenIndex < tokens.length - 1 && 
                        JPQLQueryHelper.isKeyword(tokens[tokenIndex] + ' ' + tokens[tokenIndex + 1]))
                {
                    // Invalid keyword entered ("GROUP BY", "ORDER BY") and not currently in subquery block
                    tokenIndex--;
                    break;
                }
                else
                {
                    // Append the content from the query string from the end of the last token to the end of this token
                    int endPos = queryString.indexOf(tokens[tokenIndex], queryStringPos) + tokens[tokenIndex].length();
                    String contentValue = queryString.substring(queryStringPos, endPos);
                    queryStringPos = endPos;

                    if (content.length() == 0)
                    {
                        content = contentValue;
                    }
                    else
                    {
                        content += contentValue;
                    }
                }
            }
            return content;
        }

        /**
         * Parse the next token looking for a keyword. 
         * The cursor position is skipped in one tick if a keyword is found
         * @param keyword the searched keyword
         * @return true if the keyword
         */
        public boolean parseKeywordIgnoreCase(String keyword)
        {
            if (tokenIndex < tokens.length - 1)
            {
                tokenIndex++;
                if (keywords[tokenIndex] != null)
                {
                    if (keywords[tokenIndex].equalsIgnoreCase(keyword))
                    {
                        // Move query position to end of last processed token
                        queryStringPos = 
                            queryString.indexOf(keywords[tokenIndex], queryStringPos) + 
                            keywords[tokenIndex].length()+1;
                        return true;
                    }
                    if (keyword.indexOf(' ') > -1)
                    {
                        if ((keywords[tokenIndex] + ' ' + keywords[tokenIndex + 1]).equalsIgnoreCase(keyword))
                        {
                            // Move query position to end of last processed token
                            queryStringPos =
                                queryString.indexOf(keywords[tokenIndex], queryStringPos) + 
                                keywords[tokenIndex].length()+1;
                            queryStringPos = 
                                queryString.indexOf(keywords[tokenIndex+1], queryStringPos) + 
                                keywords[tokenIndex+1].length()+1;
                            tokenIndex++;
                            return true;
                        }
                    }
                }
                tokenIndex--;
            }
            return false;
        }

        /**
         * Parse the next token looking for a keyword. The cursor position is
         * skipped in one tick if a keyword is found
         * @return the parsed keyword or null
         */
        public String parseKeyword()
        {
            if (tokenIndex < tokens.length - 1)
            {
                tokenIndex++;
                if (keywords[tokenIndex] != null)
                {
                    return keywords[tokenIndex];
                }
                tokenIndex--;
            }
            return null;
        }
    }
}