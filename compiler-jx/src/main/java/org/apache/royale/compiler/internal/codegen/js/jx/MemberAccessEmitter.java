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

import org.apache.royale.compiler.codegen.ISubEmitter;
import org.apache.royale.compiler.codegen.js.IJSEmitter;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.internal.codegen.as.ASEmitterTokens;
import org.apache.royale.compiler.internal.codegen.js.JSEmitterTokens;
import org.apache.royale.compiler.internal.codegen.js.JSSubEmitter;
import org.apache.royale.compiler.internal.codegen.js.royale.JSRoyaleDocEmitter;
import org.apache.royale.compiler.internal.codegen.js.royale.JSRoyaleEmitter;
import org.apache.royale.compiler.internal.codegen.js.royale.JSRoyaleEmitterTokens;
import org.apache.royale.compiler.internal.codegen.js.goog.JSGoogEmitterTokens;
import org.apache.royale.compiler.internal.codegen.js.jx.BinaryOperatorEmitter.DatePropertiesGetters;
import org.apache.royale.compiler.internal.definitions.AccessorDefinition;
import org.apache.royale.compiler.internal.definitions.AppliedVectorDefinition;
import org.apache.royale.compiler.internal.definitions.FunctionDefinition;
import org.apache.royale.compiler.internal.projects.RoyaleJSProject;
import org.apache.royale.compiler.internal.tree.as.*;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.ASTNodeID;
import org.apache.royale.compiler.tree.as.*;
import org.apache.royale.compiler.tree.as.IOperatorNode.OperatorType;
import org.apache.royale.compiler.utils.ASNodeUtils;

import javax.sound.midi.SysexMessage;

public class MemberAccessEmitter extends JSSubEmitter implements
        ISubEmitter<IMemberAccessExpressionNode>
{

    public MemberAccessEmitter(IJSEmitter emitter)
    {
        super(emitter);
    }

    @Override
    public void emit(IMemberAccessExpressionNode node)
    {
        if (ASNodeUtils.hasParenOpen(node))
            write(ASEmitterTokens.PAREN_OPEN);

        IASNode leftNode = node.getLeftOperandNode();
        IASNode rightNode = node.getRightOperandNode();

    	JSRoyaleEmitter fjs = (JSRoyaleEmitter)getEmitter();
        if (fjs.isDateProperty(node, false))
        {
    		writeLeftSide(node, leftNode, rightNode);
            String rightName = ((IIdentifierNode)rightNode).getName();
            DatePropertiesGetters propGetter = DatePropertiesGetters.valueOf(rightName.toUpperCase());
            write(ASEmitterTokens.MEMBER_ACCESS);
            write(propGetter.getFunctionName());
            write(ASEmitterTokens.PAREN_OPEN);
            write(ASEmitterTokens.PAREN_CLOSE);
    		return;
        }
        IDefinition def = node.resolve(getProject());
        if (def == null)
        {
        	IASNode parentNode = node.getParent();
        	// could be XML
        	boolean isXML = false;
        	boolean isProxy = false;
        	if (leftNode instanceof MemberAccessExpressionNode)
        		isXML = fjs.isLeftNodeXMLish((MemberAccessExpressionNode)leftNode);
        	else if (leftNode instanceof IExpressionNode)
        		isXML = fjs.isXML((IExpressionNode)leftNode);
        	if (leftNode instanceof MemberAccessExpressionNode)
        		isProxy = fjs.isProxy((MemberAccessExpressionNode)leftNode);
        	else if (leftNode instanceof IExpressionNode)
        		isProxy = fjs.isProxy((IExpressionNode)leftNode);
        	if (isXML)
        	{
        		boolean descendant = (node.getOperator() == OperatorType.DESCENDANT_ACCESS);
        		boolean child = (node.getOperator() == OperatorType.MEMBER_ACCESS) && 
        							(!(parentNode instanceof FunctionCallNode)) &&
        							rightNode.getNodeID() != ASTNodeID.Op_AtID &&
        							!((rightNode.getNodeID() == ASTNodeID.ArrayIndexExpressionID) && 
        									(((DynamicAccessNode)rightNode).getLeftOperandNode().getNodeID() == ASTNodeID.Op_AtID));
        		if (descendant || child)
	        	{
	        		writeLeftSide(node, leftNode, rightNode);
	        		if (descendant)
	        			write(".descendants('");
	        		if (child)
	        			write(".child('");	        			
	        		String s = fjs.stringifyNode(rightNode);
	        		int dot = s.indexOf('.');
	        		if (dot != -1)
	        		{
	        			String name = s.substring(0, dot);
	        			String afterDot = s.substring(dot);
	        			write(name);
	        			write("')");
	        			write(afterDot);
	        		}
	        		else
	        		{
	        			write(s);
	        			write("')");
	        		}
	        		return;
	        	}
        	}
        	else if (isProxy)
        	{
        		boolean child = (node.getOperator() == OperatorType.MEMBER_ACCESS) && 
        							(!(parentNode instanceof FunctionCallNode)) &&
        							rightNode.getNodeID() != ASTNodeID.Op_AtID;
        		if (child)
	        	{
	        		writeLeftSide(node, leftNode, rightNode);
	        		if (child)
	        			write(".getProperty('");
	        		String s = fjs.stringifyNode(rightNode);
	        		int dot = s.indexOf('.');
	        		if (dot != -1)
	        		{
	        			String name = s.substring(0, dot);
	        			String afterDot = s.substring(dot);
	        			write(name);
	        			write("')");
	        			write(afterDot);
	        		}
	        		else
	        		{
	        			write(s);
	        			write("')");
	        		}
	        		return;
	        	}
        	}
        	else if (rightNode instanceof NamespaceAccessExpressionNode)
        	{
        		// if you define a local variable with the same URI as a
        		// namespace that defines a namespaced property
        		// it doesn't resolve above so we handle it here
        		NamespaceAccessExpressionNode naen = (NamespaceAccessExpressionNode)rightNode;
        		IDefinition d = naen.getLeftOperandNode().resolve(getProject());
        		IdentifierNode r = (IdentifierNode)(naen.getRightOperandNode());
        		// output bracket access with QName
        		writeLeftSide(node, leftNode, rightNode);
        		write(ASEmitterTokens.SQUARE_OPEN);
        		write(ASEmitterTokens.NEW);
        		write(ASEmitterTokens.SPACE);
        		write(IASLanguageConstants.QName);
        		write(ASEmitterTokens.PAREN_OPEN);
	    		write(fjs.formatQualifiedName(d.getQualifiedName()));
        		write(ASEmitterTokens.COMMA);
        		write(ASEmitterTokens.SPACE);
        		write(ASEmitterTokens.SINGLE_QUOTE);
        		write(r.getName());
        		write(ASEmitterTokens.SINGLE_QUOTE);
        		write(ASEmitterTokens.PAREN_CLOSE);
        		write(".objectAccessFormat()");
        		write(ASEmitterTokens.SQUARE_CLOSE);
        		return;
        	}
        }
        else if (def.getParent() != null &&
        		def.getParent().getQualifiedName().equals("Array"))
        {
        	if (def.getBaseName().equals("removeAt"))
        	{
        		writeLeftSide(node, leftNode, rightNode);
        		write(".splice");
        		return;
        	}
        	else if (def.getBaseName().equals("insertAt"))
        	{
        		writeLeftSide(node, leftNode, rightNode);
        		write(".splice");
        		return;
        	}
        }
    	else if (rightNode instanceof NamespaceAccessExpressionNode)
    	{
			boolean isStatic = false;
			if (def != null && def.isStatic())
				isStatic = true;
			boolean needClosure = false;
			if (def instanceof FunctionDefinition && (!(def instanceof AccessorDefinition))
					&& !def.getBaseName().equals("constructor")) // don't wrap references to obj.constructor
			{
				IASNode parentNode = node.getParent();
				if (parentNode != null)
				{
					ASTNodeID parentNodeId = parentNode.getNodeID();
					// we need a closure if this MAE is the top-level in a chain
					// of MAE and not in a function call.
					needClosure = !isStatic && parentNodeId != ASTNodeID.FunctionCallID &&
								parentNodeId != ASTNodeID.MemberAccessExpressionID &&
								parentNodeId != ASTNodeID.ArrayIndexExpressionID;
				}
			}
			
			if (needClosure
					&& getEmitter().getDocEmitter() instanceof JSRoyaleDocEmitter
					&& ((JSRoyaleDocEmitter)getEmitter().getDocEmitter()).getSuppressClosure())
				needClosure = false;
        	if (needClosure)
        		getEmitter().emitClosureStart();

    		NamespaceAccessExpressionNode naen = (NamespaceAccessExpressionNode)rightNode;
    		IDefinition d = naen.getLeftOperandNode().resolve(getProject());
    		IdentifierNode r = (IdentifierNode)(naen.getRightOperandNode());
    		// output bracket access with QName
    		writeLeftSide(node, leftNode, rightNode);
    		if (!d.getBaseName().equals(ASEmitterTokens.PRIVATE.getToken()))
    		{
	    		write(ASEmitterTokens.SQUARE_OPEN);
	    		write(ASEmitterTokens.NEW);
	    		write(ASEmitterTokens.SPACE);
	    		write(IASLanguageConstants.QName);
	    		write(ASEmitterTokens.PAREN_OPEN);
	    		write(fjs.formatQualifiedName(d.getQualifiedName()));
	    		write(ASEmitterTokens.COMMA);
	    		write(ASEmitterTokens.SPACE);
	    		write(ASEmitterTokens.SINGLE_QUOTE);
	    		write(r.getName());
	    		write(ASEmitterTokens.SINGLE_QUOTE);
	    		write(ASEmitterTokens.PAREN_CLOSE);
        		write(".objectAccessFormat()");
	    		write(ASEmitterTokens.SQUARE_CLOSE);
    		}
    		else
    		{
                write(node.getOperator().getOperatorText());
	    		write(r.getName());    			
    		}
        
			if (needClosure)
			{
				write(ASEmitterTokens.COMMA);
				write(ASEmitterTokens.SPACE);
				if (leftNode.getNodeID() == ASTNodeID.SuperID)
					write(ASEmitterTokens.THIS);
				else
					writeLeftSide(node, leftNode, rightNode);
				getEmitter().emitClosureEnd(leftNode, def);
			}
    		return;
    	}
        boolean isCustomNamespace = false;
        if (def instanceof FunctionDefinition && node.getOperator() == OperatorType.MEMBER_ACCESS)
        	isCustomNamespace = fjs.isCustomNamespace((FunctionDefinition)def);
        boolean isStatic = false;
        if (def != null && def.isStatic())
            isStatic = true;
        boolean needClosure = false;
        if (def instanceof FunctionDefinition && (!(def instanceof AccessorDefinition))
        		&& !def.getBaseName().equals("constructor")) // don't wrap references to obj.constructor
        {
        	IASNode parentNode = node.getParent();
        	if (parentNode != null)
        	{
				ASTNodeID parentNodeId = parentNode.getNodeID();
				// we need a closure if this MAE is the top-level in a chain
				// of MAE and not in a function call.
				needClosure = !isStatic && parentNodeId != ASTNodeID.FunctionCallID &&
							parentNodeId != ASTNodeID.MemberAccessExpressionID &&
							parentNodeId != ASTNodeID.ArrayIndexExpressionID;
		
				if (needClosure
						&& getEmitter().getDocEmitter() instanceof JSRoyaleDocEmitter
						&& ((JSRoyaleDocEmitter)getEmitter().getDocEmitter()).getSuppressClosure())
					needClosure = false;
        		
        	}
        }

        boolean continueWalk = true;
        if (!isStatic)
        {
        	if (needClosure)
        		getEmitter().emitClosureStart();
        	
        	continueWalk = writeLeftSide(node, leftNode, rightNode);
        }

        if (continueWalk)
        {
			boolean emitDynamicAccess = false;
            boolean dynamicAccessUnknownMembers = false;
            ICompilerProject project = getProject();
            if(project instanceof RoyaleJSProject)
            {
                RoyaleJSProject fjsProject = (RoyaleJSProject) project;
                if(fjsProject.config != null)
                {
                    dynamicAccessUnknownMembers = fjsProject.config.getJsDynamicAccessUnknownMembers();
                }
            }
			if (dynamicAccessUnknownMembers && rightNode instanceof IIdentifierNode)
			{
				IIdentifierNode identifierNode = (IIdentifierNode) node.getRightOperandNode();
				IDefinition resolvedDefinition = identifierNode.resolve(getProject());
				emitDynamicAccess = resolvedDefinition == null;
			}
			if (emitDynamicAccess)
			{
				IIdentifierNode identifierNode = (IIdentifierNode) node.getRightOperandNode();
				startMapping(node, rightNode);
				write(ASEmitterTokens.SQUARE_OPEN);
				write(ASEmitterTokens.DOUBLE_QUOTE);
				write(identifierNode.getName());
				write(ASEmitterTokens.DOUBLE_QUOTE);
				write(ASEmitterTokens.SQUARE_CLOSE);
				endMapping(node);
			}
			else
			{
				if (!isStatic && !isCustomNamespace)
				{
					startMapping(node, node.getLeftOperandNode());
					write(node.getOperator().getOperatorText());
					endMapping(node);
				}
				getWalker().walk(node.getRightOperandNode());
			}
        }
        
        if (needClosure)
        {
        	write(ASEmitterTokens.COMMA);
        	write(ASEmitterTokens.SPACE);
        	if (leftNode.getNodeID() == ASTNodeID.SuperID)
        		write(ASEmitterTokens.THIS);
        	else
        		writeLeftSide(node, leftNode, rightNode);
        	getEmitter().emitClosureEnd(node, def);
        }
        
        if (ASNodeUtils.hasParenClose(node))
            write(ASEmitterTokens.PAREN_CLOSE);
    }

    private boolean writeLeftSide(IMemberAccessExpressionNode node, IASNode leftNode, IASNode rightNode)
    {
        if (!(leftNode instanceof ILanguageIdentifierNode && ((ILanguageIdentifierNode) leftNode)
                .getKind() == ILanguageIdentifierNode.LanguageIdentifierKind.THIS))
        {
            IDefinition rightDef = null;
            if (rightNode instanceof IIdentifierNode)
                rightDef = ((IIdentifierNode) rightNode)
                        .resolve(getProject());

            if (leftNode.getNodeID() != ASTNodeID.SuperID)
            {
                getWalker().walk(node.getLeftOperandNode());
            }
            else if (leftNode.getNodeID() == ASTNodeID.SuperID
                    && (rightNode.getNodeID() == ASTNodeID.GetterID || (rightDef != null && rightDef instanceof AccessorDefinition)))
            {
                write(getEmitter().formatQualifiedName(
                        getEmitter().getModel().getCurrentClass().getQualifiedName()));
                write(ASEmitterTokens.MEMBER_ACCESS);
                write(JSGoogEmitterTokens.SUPERCLASS);
                write(ASEmitterTokens.MEMBER_ACCESS);
                write(JSRoyaleEmitterTokens.GETTER_PREFIX);
                if (rightDef != null)
                    write(rightDef.getBaseName());
                else
                    write(((GetterNode) rightNode).getName());
                write(ASEmitterTokens.MEMBER_ACCESS);
                write(JSEmitterTokens.APPLY);
                write(ASEmitterTokens.PAREN_OPEN);
                write(ASEmitterTokens.THIS);
                write(ASEmitterTokens.PAREN_CLOSE);
                return false;
            }
            else if (leftNode.getNodeID() == ASTNodeID.SuperID
                    && (rightDef != null && rightDef instanceof FunctionDefinition))
            {
                write(getEmitter().formatQualifiedName(
                        getEmitter().getModel().getCurrentClass().getQualifiedName()));
                write(ASEmitterTokens.MEMBER_ACCESS);
                write(JSGoogEmitterTokens.SUPERCLASS);
                write(ASEmitterTokens.MEMBER_ACCESS);
                write(rightDef.getBaseName());
                return false;
            }
        }
        else
        {
            startMapping(leftNode);
            write(ASEmitterTokens.THIS);
            endMapping(leftNode);
        }
        return true;
    }
    	
}
