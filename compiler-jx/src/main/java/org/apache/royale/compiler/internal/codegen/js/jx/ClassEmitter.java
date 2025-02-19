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

package org.apache.royale.compiler.internal.codegen.js.jx;

import org.apache.royale.compiler.asdoc.royale.ASDocComment;
import org.apache.royale.compiler.codegen.ISubEmitter;
import org.apache.royale.compiler.codegen.js.IJSEmitter;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.internal.codegen.as.ASEmitterTokens;
import org.apache.royale.compiler.internal.codegen.js.JSSubEmitter;
import org.apache.royale.compiler.internal.codegen.js.royale.JSRoyaleDocEmitter;
import org.apache.royale.compiler.internal.codegen.js.royale.JSRoyaleEmitter;
import org.apache.royale.compiler.internal.codegen.js.royale.JSRoyaleEmitterTokens;
import org.apache.royale.compiler.internal.codegen.js.utils.DocEmitterUtils;
import org.apache.royale.compiler.internal.codegen.js.utils.EmitterUtils;
import org.apache.royale.compiler.internal.projects.RoyaleJSProject;
import org.apache.royale.compiler.internal.tree.as.IdentifierNode;
import org.apache.royale.compiler.tree.ASTNodeID;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IAccessorNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IDefinitionNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IVariableNode;

public class ClassEmitter extends JSSubEmitter implements
        ISubEmitter<IClassNode>
{

    public ClassEmitter(IJSEmitter emitter)
    {
        super(emitter);
    }

    @Override
    public void emit(IClassNode node)
    {
    	boolean keepASDoc = false;
        RoyaleJSProject project = (RoyaleJSProject)getEmitter().getWalker().getProject();
        keepASDoc = project.config != null && project.config.getKeepASDoc();
    	
        getModel().pushClass(node.getDefinition());

        // TODO (mschmalle) will remove this cast as more things get abstracted
        JSRoyaleEmitter fjs = (JSRoyaleEmitter) getEmitter();
        
        ASDocComment asDoc = (ASDocComment) node.getASDocComment();
        if (asDoc != null && keepASDoc)
            DocEmitterUtils.loadImportIgnores(fjs, asDoc.commentNoEnd());
        
        boolean suppressExport = (asDoc != null && DocEmitterUtils.hasSuppressExport(fjs, asDoc.commentNoEnd()));

        IClassDefinition definition = node.getDefinition();

        IFunctionDefinition ctorDefinition = definition.getConstructor();

        // look for force-linking pattern in scope block node
        int childNodeCount = node.getChildCount();
        for (int i = 0; i < childNodeCount; i++)
        {
        	IASNode child = node.getChild(i);
        	if (child.getNodeID() == ASTNodeID.BlockID)
        	{
        		int blockNodeCount = child.getChildCount();
        		for (int j = 0; j < blockNodeCount - 1; j++)
        		{
        			IASNode blockChild = child.getChild(j);
        			if (blockChild.getNodeID() == ASTNodeID.ImportID)
        			{
        				IASNode afterChild = child.getChild(j + 1);
        				if (afterChild.getNodeID() == ASTNodeID.IdentifierID)
        				{
        					IDefinition def = ((IdentifierNode)afterChild).resolve(project);
        					if (def instanceof IClassDefinition)
        					{
        						fjs.usedNames.add(def.getQualifiedName());
        					}
        				}
        			}
        		}
        		break;
        	}        	
        }
        
        // Static-only (Singleton) classes may not have a constructor
        if (ctorDefinition != null)
        {
            IFunctionNode ctorNode = (IFunctionNode) ctorDefinition.getNode();
            if (ctorNode != null)
            {
                // constructor
                getEmitter().emitMethod(ctorNode);
                write(ASEmitterTokens.SEMICOLON);
            }
            else
            {
                String qname = definition.getQualifiedName();
                if (qname != null && !qname.equals(""))
                {
                    write(getEmitter().formatQualifiedName(qname));
                    write(ASEmitterTokens.SPACE);
                    writeToken(ASEmitterTokens.EQUAL);
                    write(ASEmitterTokens.FUNCTION);
                    write(ASEmitterTokens.PAREN_OPEN);
                    write(ASEmitterTokens.PAREN_CLOSE);
                    write(ASEmitterTokens.SPACE);
                    write(ASEmitterTokens.BLOCK_OPEN);
                    writeNewline();
                    fjs.emitComplexInitializers(node);
                    write(ASEmitterTokens.BLOCK_CLOSE);
                    write(ASEmitterTokens.SEMICOLON);
                }
            }
        }

  	    if (!getEmitter().getModel().isExterns && !suppressExport)
  	    {
  	        JSRoyaleDocEmitter doc = (JSRoyaleDocEmitter) getEmitter()
  	        .getDocEmitter();
  		    writeNewline();
  		    writeNewline();
  		    writeNewline();
  		    doc.begin();
  		    writeNewline(" * Prevent renaming of class. Needed for reflection.");
  		    doc.end();
  		    write(JSRoyaleEmitterTokens.GOOG_EXPORT_SYMBOL);
  		    write(ASEmitterTokens.PAREN_OPEN);
  		    write(ASEmitterTokens.SINGLE_QUOTE);
  		    write(getEmitter().formatQualifiedName(node.getQualifiedName()));
  		    write(ASEmitterTokens.SINGLE_QUOTE);
  		    write(ASEmitterTokens.COMMA);
  		    write(ASEmitterTokens.SPACE);
  		    write(getEmitter().formatQualifiedName(node.getQualifiedName()));
  		    write(ASEmitterTokens.PAREN_CLOSE);
  		    write(ASEmitterTokens.SEMICOLON);
  	    }

        IDefinitionNode[] dnodes = node.getAllMemberNodes();
        for (IDefinitionNode dnode : dnodes)
        {
            if (dnode.getNodeID() == ASTNodeID.VariableID)
            {
                writeNewline();
                writeNewline();
                writeNewline();
                getEmitter().emitField((IVariableNode) dnode);
                startMapping(dnode, dnode);
                write(ASEmitterTokens.SEMICOLON);
                endMapping(dnode);
            }
            else if (dnode.getNodeID() == ASTNodeID.FunctionID)
            {
                if (!((IFunctionNode) dnode).isConstructor())
                {
                    writeNewline();
                    writeNewline();
                    writeNewline();
                    getEmitter().emitMethod((IFunctionNode) dnode);
                    write(ASEmitterTokens.SEMICOLON);
                }
            }
            else if (dnode.getNodeID() == ASTNodeID.GetterID
                    || dnode.getNodeID() == ASTNodeID.SetterID)
            {
                //writeNewline();
                //writeNewline();
                //writeNewline();
                fjs.emitAccessors((IAccessorNode) dnode);
                //this shouldn't write anything, just set up
                //a data structure for emitASGettersAndSetters
                //write(ASEmitterTokens.SEMICOLON);
            }
            else if (dnode.getNodeID() == ASTNodeID.BindableVariableID)
            {
                writeNewline();
                writeNewline();
                writeNewline();
                getEmitter().emitField((IVariableNode) dnode);
                startMapping(dnode, dnode);
                write(ASEmitterTokens.SEMICOLON);
                endMapping(dnode);
            }
        }

        fjs.getBindableEmitter().emit(definition);
        fjs.getAccessorEmitter().emit(definition);
        
        if (fjs.getFieldEmitter().hasComplexStaticInitializers)
        {
            writeNewline();
            boolean complexInitOutput = false;
	        for (IDefinitionNode dnode : dnodes)
	        {
	            if (dnode.getNodeID() == ASTNodeID.VariableID)
	            {
                    complexInitOutput = fjs.getFieldEmitter().emitFieldInitializer((IVariableNode) dnode) || complexInitOutput;
	            }
	            else if (dnode.getNodeID() == ASTNodeID.BindableVariableID)
	            {
                    complexInitOutput = fjs.getFieldEmitter().emitFieldInitializer((IVariableNode) dnode) || complexInitOutput;
	            }
	        }
	        if (complexInitOutput) {
                writeNewline();
                writeNewline();
            }
        }
        
        fjs.getPackageFooterEmitter().emitClassInfo(node);

        getModel().popClass();
    }
    
    public void emitComplexInitializers(IClassNode node)
    {
    	boolean wroteOne = false;
        IDefinitionNode[] dnodes = node.getAllMemberNodes();
        for (IDefinitionNode dnode : dnodes)
        {
            if (dnode.getNodeID() == ASTNodeID.VariableID || dnode.getNodeID() == ASTNodeID.BindableVariableID)
            {
            	IVariableNode varnode = ((IVariableNode)dnode);
                IExpressionNode vnode = varnode.getAssignedValueNode();
                if (vnode != null && (!(dnode.getDefinition().isStatic() || EmitterUtils.isScalar(vnode))))
                {
                    writeNewline();
                    write(ASEmitterTokens.THIS);
                    write(ASEmitterTokens.MEMBER_ACCESS);
                    String dname = dnode.getName();
                    IDefinition dDef = dnode.getDefinition();
                		if (dDef != null && dDef.isPrivate() && getProject().getAllowPrivateNameConflicts())
                			dname = getEmitter().formatPrivateName(dDef.getParent().getQualifiedName(), dname);
                    write(dname);
                    if (dnode.getNodeID() == ASTNodeID.BindableVariableID)
                    {
                    	write("_");
                    }
                    write(ASEmitterTokens.SPACE);
                    writeToken(ASEmitterTokens.EQUAL);
                    getEmitter().emitAssignmentCoercion(vnode, varnode.getVariableTypeNode().resolve(getProject()));
                    write(ASEmitterTokens.SEMICOLON);
                    wroteOne = true;
                }
            }
        }    
        if (wroteOne)
        	writeNewline();
    }
}
