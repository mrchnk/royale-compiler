/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.royale.compiler.internal.codegen.js;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Stack;

import org.apache.royale.compiler.codegen.ISourceMapEmitter;
import org.apache.royale.compiler.codegen.js.IJSEmitter;
import org.apache.royale.compiler.codegen.js.IJSWriter;
import org.apache.royale.compiler.codegen.js.IMappingEmitter;
import org.apache.royale.compiler.driver.js.IJSBackend;
import org.apache.royale.compiler.internal.projects.RoyaleJSProject;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.visitor.as.IASBlockWalker;

public class JSWriter implements IJSWriter
{
    protected RoyaleJSProject project;

    protected List<ICompilerProblem> problems;

    protected ICompilationUnit compilationUnit;

    @SuppressWarnings("unused")
    private boolean enableDebug;

    private boolean isExterns = false;

    public boolean isExterns()
    {
    	return this.isExterns;
    }
    
    /**
     * Create a JSApplication writer.
     * 
     * @param application the JSApplication model to be encoded
     * @param useCompression use ZLIB compression if true
     */
    public JSWriter(RoyaleJSProject project, List<ICompilerProblem> problems,
            ICompilationUnit compilationUnit, boolean enableDebug)
    {
        this.project = project;
        this.problems = problems;
        this.compilationUnit = compilationUnit;
        this.enableDebug = enableDebug;
    }

    @Override
    public void close() throws IOException
    {
        //outputBuffer.close();
    }

    @Override
    public void writeTo(OutputStream out)
    {
        writeTo(out, null, null);
    }

    @Override
    public int writeTo(File out) throws IOException
    {
        return 0;
    }

    public void writeTo(OutputStream jsOut, OutputStream jsSourceMapOut, File sourceMapFile)
    {
        IJSBackend backend = (IJSBackend) project.getBackend();
        JSFilterWriter writer = (JSFilterWriter) backend.createWriterBuffer(project);
        IJSEmitter emitter = (IJSEmitter) backend.createEmitter(writer);
        IASBlockWalker walker = backend.createWalker(project, problems, emitter);

        walker.visitCompilationUnit(compilationUnit);

        isExterns = emitter.getModel().isExterns;
        
        try
        {
            jsOut.write(emitter.postProcess(writer.toString()).getBytes("utf8"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if (jsSourceMapOut != null)
        {
            String sourceMapFilePath = null;
            String sourceRoot = null;
            if (sourceMapFile != null)
            {
                sourceMapFilePath = sourceMapFile.getAbsolutePath();
                convertMappingSourcePathsToRelative(emitter, sourceMapFile);
            }
            else
            {
                sourceRoot = System.getProperty("user.dir");
                convertMappingSourcePathsToRelative((IMappingEmitter) emitter, new File(sourceRoot, "test.js.map"));
                sourceRoot = convertSourcePathToURI(sourceRoot);
            }
            convertMappingSourcePathsToURI(emitter);

            File compilationUnitFile = new File(compilationUnit.getAbsoluteFilename());
            ISourceMapEmitter sourceMapEmitter = backend.createSourceMapEmitter(emitter);
            try
            {
                String fileName = compilationUnitFile.getName();
                fileName = fileName.replace(".as", ".js");
                String sourceMap = sourceMapEmitter.emitSourceMap(fileName, sourceMapFilePath, sourceRoot);
                jsSourceMapOut.write(sourceMap.getBytes("utf8"));
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    
    protected void convertMappingSourcePathsToRelative(IMappingEmitter emitter, File relativeToFile)
    {
        List<IMappingEmitter.SourceMapMapping> mappings = emitter.getSourceMapMappings();
        for (IMappingEmitter.SourceMapMapping mapping : mappings)
        {
            String relativePath = relativize(mapping.sourcePath, relativeToFile.getAbsolutePath());
            mapping.sourcePath = relativePath;
        }
    }
    
    protected void convertMappingSourcePathsToURI(IMappingEmitter emitter)
    {
        List<IMappingEmitter.SourceMapMapping> mappings = emitter.getSourceMapMappings();
        for (IMappingEmitter.SourceMapMapping mapping : mappings)
        {
            //prefer forward slash because web browser devtools expect it
            String sourcePath = mapping.sourcePath;
            sourcePath = convertSourcePathToURI(sourcePath);
            mapping.sourcePath = sourcePath;
        }
    }
    
    protected String convertSourcePathToURI(String sourcePath)
    {
        File file = new File(sourcePath);
        if(file.isAbsolute())
        {
            sourcePath = "file:///" + sourcePath;
        }
        return sourcePath.replace('\\', '/');
    }

    //if we ever support Java 7, the java.nio.file.Path relativize() method
    //should be able to replace this method
    private String relativize(String filePath, String relativeToFilePath)
    {
        boolean caseInsensitive = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if(caseInsensitive)
        {
            relativeToFilePath = relativeToFilePath.toLowerCase();
        }
        File currentFile = new File(filePath);
        Stack<String> stack = new Stack<String>();
        stack.push(currentFile.getName());
        currentFile = currentFile.getParentFile();
        while (currentFile != null)
        {
            String absoluteCurrentFile = currentFile.getAbsolutePath() + File.separator;
            if(caseInsensitive)
            {
                absoluteCurrentFile = absoluteCurrentFile.toLowerCase();
            }
            if (relativeToFilePath.startsWith(absoluteCurrentFile))
            {
                String relativeRelativeToFile = relativeToFilePath.substring(absoluteCurrentFile.length());
                int separatorCount = relativeRelativeToFile.length() - relativeRelativeToFile.replace(File.separator, "").length();
                String result = "";
                while (separatorCount > 0)
                {
                    result += ".." + File.separator;
                    separatorCount--;
                }
                while (stack.size() > 0)
                {
                    result += stack.pop();
                }
                return result;
            }
            stack.push(currentFile.getName() + File.separator);
            currentFile = currentFile.getParentFile();
        }
        return null;
    }
}
