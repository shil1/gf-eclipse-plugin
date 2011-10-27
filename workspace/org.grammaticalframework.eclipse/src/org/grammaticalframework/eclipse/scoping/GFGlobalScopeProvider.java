package org.grammaticalframework.eclipse.scoping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.*;
import org.grammaticalframework.eclipse.gF.Ident;
import org.grammaticalframework.eclipse.gF.Included;
import org.grammaticalframework.eclipse.gF.ModDef;
import org.grammaticalframework.eclipse.gF.Open;

import com.google.common.base.Predicate;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Global scope provider is responsible for defining what is visible from
 * outside the current resource, for any given reference.
 * 
 * In our case, this means considering;
 * - Anything exended/inherited in this resource (remember inheritance is transitive)
 * - Anything opened in this resource
 * - If this is a concrete module, anything in its abstract
 * (where "this" means the resource in which the reference is defined)
 * 
 * My implementation is heavily based on ImportUriGlobalScopeProvider
 * 
 */

public class GFGlobalScopeProvider extends AbstractGlobalScopeProvider {

	@Inject
	private Provider<LoadOnDemandResourceDescriptions> loadOnDemandDescriptions;
	
	public void setLoadOnDemandDescriptions(Provider<LoadOnDemandResourceDescriptions> loadOnDemandDescriptions) {
		this.loadOnDemandDescriptions = loadOnDemandDescriptions;
	}

	public Provider<LoadOnDemandResourceDescriptions> getLoadOnDemandDescriptions() {
		return loadOnDemandDescriptions;
	}
	
	@Inject
	private GFLibraryAgent libAgent;

// TODO Implement caching for Global Scope Provider
//	@Inject
//	private IResourceScopeCache cache;
//	
//	public void setCache(IResourceScopeCache cache) {
//		this.cache = cache;
//	}
	
	public IResourceDescriptions getResourceDescriptions(Resource resource, Collection<URI> importUris) {
		IResourceDescriptions result = getResourceDescriptions(resource);
		LoadOnDemandResourceDescriptions demandResourceDescriptions = loadOnDemandDescriptions.get();
		demandResourceDescriptions.initialize(result, importUris, resource);
		return demandResourceDescriptions;
	}

	@Override
	protected IScope getScope(Resource resource, boolean ignoreCase, EClass type, Predicate<IEObjectDescription> filter) {
		
		// Start from nothing
		IScope scope = IScope.NULLSCOPE;
	
		// (try) get module definition
		ModDef moduleDef;
		try {
			moduleDef = (ModDef)resource.getContents().get(0);
		} catch (Exception _) {
			return scope;
		}

		boolean ignoreModuleName = true;

		// Is this a concrete/instance of an abstract/interface module?
		if (moduleDef.getType().isConcrete() || moduleDef.getType().isInstance()) {
			String moduleName = moduleDef.getType().getAbstractName().getS();
			try {
				ignoreModuleName = true;
				scope = addModuleToScope(moduleName, scope, resource, type, filter, ignoreCase, ignoreModuleName);
			} catch (NullPointerException _) {
//				System.out.println("#1 Error loading " + moduleName);
			}
		}

		// Handle inheritance
		EList<Included> modExtends = moduleDef.getBody().getExtends(); 
		for (final Included i : modExtends) {
			String moduleName = i.getName().getS();
			ignoreModuleName = true;

			// Create filters for selective inheritance
			if (i.isInclusive()) {
				final ArrayList<String> includes = new ArrayList<String>();
				for (Ident id : i.getIncludes()) {
					includes.add(id.getS());
				}
				filter = new Predicate<IEObjectDescription>() {
					public boolean apply(IEObjectDescription input) {
						return includes.contains(input.getName().getLastSegment());
					}
				};
			}
			else if (i.isExclusive()) {
				final ArrayList<String> excludes = new ArrayList<String>();
				for (Ident id : i.getExcludes()) {
					excludes.add(id.getS());
				}
				filter = new Predicate<IEObjectDescription>() {
					public boolean apply(IEObjectDescription input) {
						return !excludes.contains(input.getName().getLastSegment());
					}
				};
			}

			scope = addModuleToScope(moduleName, scope, resource, type, filter, ignoreCase, ignoreModuleName);
		}
		
		// Handle opening
		EList<Open> modOpens = moduleDef.getBody().getOpens(); 
		for (Open o : modOpens) {
			String abstractModuleName = o.getName().getS();
			if (o.getAlias()!=null) {
				// Add everything with the alias'd qualified names
				scope = addModuleToScope(abstractModuleName, scope, resource, type, filter, ignoreCase, false, o.getAlias().getS());
			} else {
				// Add everything without qualified names
				scope = addModuleToScope(abstractModuleName, scope, resource, type, filter, ignoreCase, true);
			}
			// ALWAYS include a version with the "true" qualified names
			scope = addModuleToScope(abstractModuleName, scope, resource, type, filter, ignoreCase, false);
		}
		
		// Handle interface instantiations (behave as inheritance)
		EList<Open> modInstantiations = moduleDef.getBody().getInstantiations();
		for (Open o : modInstantiations) {
			String moduleName = o.getAlias().getS();
			scope = addModuleToScope(moduleName, scope, resource, type, filter, ignoreCase, ignoreModuleName);
		}
		
		// Phew
		return scope;
	}
	
	
	/**
	 * Add a named module recursively to the given scope.
	 * 
	 * @param moduleName The name of the module whos definitions we want to add
	 * @param resource The context from where we are referencing the named module
	 * @param parent The scope to which definitions should be appended
	 * @param filter
	 * @param ignoreCase
	 * @param ignoreModuleName
	 * @param moduleAlias
	 * 
	 * @return new scope
	 */
	private IScope addModuleToScope(String moduleName, IScope scope, Resource resource, EClass type, Predicate<IEObjectDescription> filter,
			boolean ignoreCase, boolean ignoreModuleName) {
		return addModuleToScope(moduleName, scope, resource, type, filter, ignoreCase, ignoreModuleName, null);
	}	
	private IScope addModuleToScope(String moduleName, IScope scope, Resource resource, EClass type, Predicate<IEObjectDescription> filter,
			boolean ignoreCase, boolean ignoreModuleName, String moduleAlias) {
		
		// Add the stuff defined IN the named module
		if (scope.equals(IScope.NULLSCOPE)) {
			scope = addModuleDefinitionsToScope(moduleName, resource, scope, filter, ignoreCase, ignoreModuleName, moduleAlias);
		} else {
			// We need to preserve what's already there
			// TODO This might create an un-nice tree, but it works for now. Ideally preserve proper linked list structure.
			//scope = new SimpleScope(parentScope, scope.getAllElements(), ignoreCase);
			scope = new SimpleScope(scope, scope.getAllElements(), ignoreCase);
			scope = addModuleDefinitionsToScope(moduleName, resource, scope, filter, ignoreCase, ignoreModuleName, moduleAlias);
		}

		// Recurse into the named module's parents and get their defs
		Resource namedModuleResource = libAgent.getModuleResource(resource, moduleName);
		IScope namedModuleScope = getScope(namedModuleResource, ignoreCase, type, filter);
		scope = new SimpleScope(scope, namedModuleScope.getAllElements(), ignoreCase);
	
		// Return it hux
		return scope;
	} 

	/**
	 * Add the defitions in a named module to the given scope.
	 * 
	 * @param moduleName The name of the module whos definitions we want to add
	 * @param resource The context from where we are referencing the named module
	 * @param parent The scope to which definitions should be appended
	 * @param filter
	 * @param ignoreCase
	 * @param ignoreModuleName
	 * @param moduleAlias
	 * 
	 * @return new scope
	 */
	private IScope addModuleDefinitionsToScope(String moduleName, Resource resource, IScope parent, Predicate<IEObjectDescription> filter,
			boolean ignoreCase, boolean ignoreModuleName, String moduleAlias) {

		try {
			URI uri = libAgent.getModuleURI(resource, moduleName);
			if (uri == null)
				return parent;
		
			// Get resource descriptions
			final LinkedHashSet<URI> uriAsCollection = new LinkedHashSet<URI>(1);
			uriAsCollection.add(uri);
			IResourceDescriptions descriptions = getResourceDescriptions(resource, uriAsCollection);
		
			// Extract just our resource desc and return a scope out of it
			IResourceDescription irdesc = descriptions.getResourceDescription(uri);
			if (moduleAlias != null)
				return new GFScope(parent, irdesc.getExportedObjects(), filter, ignoreCase, moduleAlias);
			else
				return new GFScope(parent, irdesc.getExportedObjects(), filter, ignoreCase, ignoreModuleName);
			
		} catch (NullPointerException e) {
			// Most likely the moduleName does not exist
			System.out.println(String.format("#3 Error loading %s from %s", moduleName, resource.getURI().toString()));
		} catch (IllegalStateException e) {
			// This was a problem before I registered .gfh as a file extension... hopefully not anymore
			e.printStackTrace();
		}
		return parent;
	}
}
