/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Axis" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.axis.components.compiler;

import org.apache.axis.components.logger.LogFactory;
import org.apache.axis.utils.ClassUtils;
import org.apache.axis.utils.Messages;
import org.apache.commons.logging.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * This class wraps the Sun's Javac Compiler.
 *
 * @author <a href="mailto:dims@yahoo.com">Davanum Srinivas</a>
 * @author <a href="mailto:stefano@apache.org">Stefano Mazzocchi</a>
 * @version $Revision: 1.12 $ $Date: 2002/07/02 18:07:35 $
 * @since 2.0
 */

public class Javac extends AbstractCompiler
{
    protected static Log log =
        LogFactory.getLog(Javac.class.getName());

    public static final String CLASSIC_CLASS = "sun.tools.javac.Main";
    public static final String MODERN_CLASS = "com.sun.tools.javac.Main";

    private boolean modern = false;

    public Javac() {

        // Use reflection to be able to build on all JDKs
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            ClassUtils.forName(MODERN_CLASS, true, cl);
            modern = true;
        } catch (ClassNotFoundException e) {
            log.debug(Messages.getMessage("noModernCompiler"));
            try {
                ClassUtils.forName(CLASSIC_CLASS, true, cl);
                modern = false;
            } catch (Exception ex) {
                log.error(Messages.getMessage("noCompiler00"), ex);
                throw new RuntimeException(Messages.getMessage("noCompiler00"));
            }
        }
        log.debug(Messages.getMessage("compilerClass",
                (modern ? MODERN_CLASS : CLASSIC_CLASS)));
    }

    /**
     * Compile a source file yielding a loadable class file.
     *
     * @exception IOException If an error occurs during compilation
     */
    public boolean compile() throws IOException {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        boolean result = false;

        try {
            // Create an instance of the compiler, redirecting output to err
            Class c = ClassUtils.forName("sun.tools.javac.Main");
            Constructor cons =
                c.getConstructor(new Class[] { OutputStream.class,
                                               String.class });
            Object compiler = cons.newInstance(new Object[] { err,
                                                              "javac" });
            // Call the compile() method
            Method compile = c.getMethod("compile",
                                         new Class [] { String[].class });
            Boolean ok =
                (Boolean) compile.invoke(compiler,
                                        new Object[] {toStringArray(fillArguments(new ArrayList()))});
            result = ok.booleanValue();
        } catch (Exception cnfe){
            log.error(Messages.getMessage("noCompiler00"), cnfe);
            throw new RuntimeException(Messages.getMessage("noCompiler00"));
        }

        this.errors = new ByteArrayInputStream(err.toByteArray());
        return result;
    }

    /**
     * Parse the compiler error stream to produce a list of
     * <code>CompilerError</code>s
     *
     * @param input The error stream
     * @return The list of compiler error messages
     * @exception IOException If an error occurs during message collection
     */
    protected List parseStream(BufferedReader input) throws IOException {
        if (modern) {
            return parseModernStream(input);
        } else {
            return parseClassicStream(input);
        }
    }

    /**
     * Parse the compiler error stream to produce a list of
     * <code>CompilerError</code>s
     *
     * @param input The error stream
     * @return The list of compiler error messages
     * @exception IOException If an error occurs during message collection
     */
    protected List parseModernStream(BufferedReader input) throws IOException {
        List errors = new ArrayList();
        String line = null;
        StringBuffer buffer = null;

        while (true) {
            // cleanup the buffer
            buffer = new StringBuffer(); // this is quicker than clearing it

            // most errors terminate with the '^' char
            do {
                if ((line = input.readLine()) == null)
                {
                    if (buffer.length() > 0) {
                        // There's an error which doesn't end with a '^'
                        errors.add(new CompilerError("\n" + buffer.toString()));
                    }
                    return errors;
                }
                log.debug(line);
                buffer.append(line);
                buffer.append('\n');
            } while (!line.endsWith("^"));

            // add the error bean
            errors.add(parseModernError(buffer.toString()));
        }
    }

    /**
     * Parse an individual compiler error message with modern style.
     *
     * @param error The error text
     * @return A messaged <code>CompilerError</code>
     */
    private CompilerError parseModernError(String error) {
        StringTokenizer tokens = new StringTokenizer(error, ":");
        try {
            String file = tokens.nextToken();
            if (file.length() == 1) file = new StringBuffer(file).append(":").append(tokens.nextToken()).toString();
            int line = Integer.parseInt(tokens.nextToken());

            String message = tokens.nextToken("\n").substring(1);
            String context = tokens.nextToken("\n");
            String pointer = tokens.nextToken("\n");
            int startcolumn = pointer.indexOf("^");
            int endcolumn = context.indexOf(" ", startcolumn);
            if (endcolumn == -1) endcolumn = context.length();
            return new CompilerError(file, false, line, startcolumn, line, endcolumn, message);
        } catch(NoSuchElementException nse) {
            return new CompilerError(Messages.getMessage("noMoreTokens", error));
        } catch(Exception nse) {
            return new CompilerError(Messages.getMessage("cantParse", error));
        }
    }

    /**
     * Parse the compiler error stream to produce a list of
     * <code>CompilerError</code>s
     *
     * @param input The error stream
     * @return The list of compiler error messages
     * @exception IOException If an error occurs during message collection
     */
    protected List parseClassicStream(BufferedReader input) throws IOException {
        List errors = null;
        String line = null;
        StringBuffer buffer = null;

        while (true) {
            // cleanup the buffer
            buffer = new StringBuffer(); // this is faster than clearing it

            // each error has 3 lines
            for (int i = 0; i < 3 ; i++) {
                if ((line = input.readLine()) == null) return errors;
                log.debug(line);
                buffer.append(line);
                buffer.append('\n');
            }

            // if error is found create the vector
            if (errors == null) errors = new ArrayList();

            // add the error bean
            errors.add(parseClassicError(buffer.toString()));
        }
    }

    /**
     * Parse an individual compiler error message with classic style.
     *
     * @param error The error text
     * @return A messaged <code>CompilerError</code>
     */
    private CompilerError parseClassicError(String error) {
        StringTokenizer tokens = new StringTokenizer(error, ":");
        try {
            String file = tokens.nextToken();
            if (file.length() == 1) {
                file = new StringBuffer(file).append(":").
                        append(tokens.nextToken()).toString();
            }
            int line = Integer.parseInt(tokens.nextToken());

            String last = tokens.nextToken();
            // In case the message contains ':', it should be reassembled
            while (tokens.hasMoreElements()) {
                last += tokens.nextToken();
            }
            tokens = new StringTokenizer(last.trim(), "\n");
            String message = tokens.nextToken();
            String context = tokens.nextToken();
            String pointer = tokens.nextToken();
            int startcolumn = pointer.indexOf("^");
            int endcolumn = context.indexOf(" ", startcolumn);
            if (endcolumn == -1) endcolumn = context.length();

            return new CompilerError(srcDir + File.separator + file, true,
                    line, startcolumn, line, endcolumn, message);
        } catch(NoSuchElementException nse) {
            return new CompilerError(Messages.getMessage("noMoreTokens",
                    error));
        } catch(Exception nse) {
            return new CompilerError(Messages.getMessage("cantParse", error));
        }
    }

    public String toString() {
        return Messages.getMessage("sunJavac");
    }
}
