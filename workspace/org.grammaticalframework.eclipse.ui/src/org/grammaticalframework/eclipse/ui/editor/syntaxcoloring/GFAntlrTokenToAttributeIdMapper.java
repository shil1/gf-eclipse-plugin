/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2012
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement no. FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.ui.editor.syntaxcoloring;

import org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultAntlrTokenToAttributeIdMapper;

public class GFAntlrTokenToAttributeIdMapper extends DefaultAntlrTokenToAttributeIdMapper {

	protected String calculateId(String tokenName, int tokenType) {
		if("RULE_ID".equals(tokenName)) {
			return GFHighlightingConfiguration.ID_ID;
		}
		if("RULE_INTEGER".equals(tokenName) || "RULE_DOUBLE".equals(tokenName)) {
			return GFHighlightingConfiguration.NUMBER_ID;
		}
		if("RULE_COMPILER_PRAGMA".equals(tokenName)) {
			return GFHighlightingConfiguration.COMPILER_PRAGMA_ID;
		}
		if("RULE_GF_DOC".equals(tokenName)) {
			return GFHighlightingConfiguration.GF_DOC_ID;
		}
		return super.calculateId(tokenName, tokenType);
	}
}
