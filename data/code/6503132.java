/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat, Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.reddeer.swt.generator.framework.rules.complex;

import java.util.List;

import org.eclipse.swtbot.generator.framework.GenerationComplexRule;
import org.eclipse.swtbot.generator.framework.GenerationSimpleRule;
import org.eclipse.swtbot.generator.listener.WorkbenchListener;
import org.eclipse.reddeer.swt.generator.framework.rules.simple.CTabWorkbenchRule;

public class CTabWorkbenchFilterComplexRule extends GenerationComplexRule{

	@Override
	public boolean appliesToPartially(GenerationSimpleRule rule, int i) {
		if(rule instanceof CTabWorkbenchRule && ((CTabWorkbenchRule)rule).getDetail() != WorkbenchListener.PART_CLOSED){
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean appliesTo(List<GenerationSimpleRule> rules) {
		for(GenerationSimpleRule r: rules){
			if(! (r instanceof CTabWorkbenchRule)){
				return false;
			}
		}
		return true;
	}

	@Override
	public List<String> getActions() {
		return ((CTabWorkbenchRule)getInitializationRules().get(getInitializationRules().size()-1)).getActions();
	}

	@Override
	public List<String> getImports() {
		return ((CTabWorkbenchRule)getInitializationRules().get(getInitializationRules().size()-1)).getImports();
	}
}
