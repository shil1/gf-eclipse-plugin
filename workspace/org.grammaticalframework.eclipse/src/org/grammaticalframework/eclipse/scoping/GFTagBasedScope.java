package org.grammaticalframework.eclipse.scoping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.AbstractScope;
import org.grammaticalframework.eclipse.gF.GFFactory;
import org.grammaticalframework.eclipse.gF.Ident;
import org.grammaticalframework.eclipse.gF.impl.IdentImpl;

import com.google.inject.Inject;

public class GFTagBasedScope extends AbstractScope {

	/**
	 * Qualified name converter
	 */
	@Inject
	private IQualifiedNameConverter converter = new IQualifiedNameConverter.DefaultImpl();

	/**
	 * The library agent.
	 */
	@Inject
	private GFLibraryAgent libAgent = new GFLibraryAgent();
	

	/**
	 * The name of the module this scope represents
	 */
	private final String moduleName;
	
	/**
	 * The object descriptions.
	 */
	private final ArrayList<IEObjectDescription> descriptions;
	
	/**
	 * Blank constructor
	 * 
	 * @param parent
	 * @param ignoreCase
	 */
	protected GFTagBasedScope(IScope parent, String moduleName, boolean ignoreCase) {
		super(parent==null ? IScope.NULLSCOPE : parent, ignoreCase);
		this.moduleName = moduleName;
		descriptions = new ArrayList<IEObjectDescription>();
	}
	
	/**
	 * Constructs a new scope with the given descriptions
	 * 
	 * @param parent
	 * @param ignoreCase
	 */
	protected GFTagBasedScope(IScope parent, IResourceDescription resourceDescription, boolean ignoreCase) {
		super(parent==null ? IScope.NULLSCOPE : parent, ignoreCase);
		moduleName = resourceDescription.getURI().lastSegment().substring(0, resourceDescription.getURI().lastSegment().lastIndexOf('.'));
		descriptions = new ArrayList<IEObjectDescription>();
		for (IEObjectDescription desc : resourceDescription.getExportedObjects()) {
			QualifiedName newName = getUnQualifiedName(desc.getQualifiedName()); // Strip off qualified bit of name (maybe)
			descriptions.add(EObjectDescription.create(newName, desc.getEObjectOrProxy()));
		}
	}
	
	public void addTags(Resource context, Collection<TagEntry> tags) {
		for (TagEntry tag : tags) {
			addTag(context, tag);
		}
	}
	public void addTag(Resource context, TagEntry tag) {
		QualifiedName fullyQualifiedName = converter.toQualifiedName(tag.getIdent());
		QualifiedName unQualifiedName = getUnQualifiedName(fullyQualifiedName);
		
		EObject eObject = null;
		try {
			eObject = libAgent.findEObjectInFile(context, tag.getFile(), unQualifiedName.toString());
		} catch (RuntimeException _) {	}
		if (eObject == null) {
			// Just create a dummy eObject, to satisfy the validator
			IdentImpl id = (IdentImpl) GFFactory.eINSTANCE.createIdent();
			id.setS(tag.getIdent());
			URI uri = URI.createFileURI(tag.getFile()).appendFragment("///@body/@judgements.0/@definitions.0/@name");
			id.eSetProxyURI(uri);
			eObject = id;
		}
		
		Map<String, String> userData = tag.getProperties();
		IEObjectDescription eObjectDescription = new EObjectDescription(unQualifiedName, eObject, userData);
		descriptions.add(eObjectDescription);
	}
	
	private QualifiedName getUnQualifiedName(QualifiedName qn) {
		return qn.skipFirst(qn.getSegmentCount()-1);
	}
	
	@Override
	protected Iterable<IEObjectDescription> getAllLocalElements() {
		return descriptions;
	}

	protected int localElementCount() {
		return descriptions.size();
	}

	protected String getModuleName() {
		return moduleName;
	}

}
