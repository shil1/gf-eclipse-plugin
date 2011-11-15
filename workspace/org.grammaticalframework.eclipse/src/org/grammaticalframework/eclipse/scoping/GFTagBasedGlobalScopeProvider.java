/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2011
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement n° FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.scoping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.*;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.grammaticalframework.eclipse.builder.GFBuilder;
import org.grammaticalframework.eclipse.gF.ModDef;
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
 */

public class GFTagBasedGlobalScopeProvider extends AbstractGlobalScopeProvider {
	
	/**
	 * The logger
	 */
	private static final Logger log = Logger.getLogger(GFTagBasedGlobalScopeProvider.class);

	/**
	 * Instantiates a new gF global scope provider.
	 */
	public GFTagBasedGlobalScopeProvider() {
		super();
	}
	
	@Inject
	private GFLibraryAgent libAgent;
	
	@Inject
	private ExtensibleURIConverterImpl uriConverter; 
	
	@Inject
	private IResourceScopeCache cache;
	
	public void setCache(IResourceScopeCache cache) {
		this.cache = cache;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.xtext.scoping.impl.AbstractGlobalScopeProvider#getScope(org.eclipse.emf.ecore.resource.Resource, boolean, org.eclipse.emf.ecore.EClass, com.google.common.base.Predicate)
	 */
	@Override
	protected IScope getScope(Resource resource, boolean ignoreCase, EClass type, Predicate<IEObjectDescription> filter) {
		
		/* ----- Method 1: Just use the URIs ----- */
//		// Load all descriptions from all mentioned files/URIs
//		Set<URI> uniqueImportURIs = getImportedURIs(resource);
//		IResourceDescriptions resourceDescriptions = getResourceDescriptions(resource, uniqueImportURIs);
//
//		// Add everything from all the URIs mentioned in the tags file
//		IScope scope = IScope.NULLSCOPE;
//		for (IResourceDescription resDesc : resourceDescriptions.getAllResourceDescriptions()) {
//			GFTagBasedScope newScope = new GFTagBasedScope(scope, resDesc, ignoreCase);
//			if (newScope.localElementCount() > 0)
//				scope = newScope;
//		}
//		
//		return scope;

		/* ----- Method 2: Use the tags themselves ----- */
		GFTagBasedScope gfScope = null;
		Map<URI, Collection<TagEntry>> uriTagMap = getURITagMap(resource);
		for (Map.Entry<URI, Collection<TagEntry>> entry : uriTagMap.entrySet()) {
			String lastSegment = entry.getKey().lastSegment();
			int dotIx = lastSegment.lastIndexOf('.');
			String moduleName = (dotIx > 0)	? lastSegment.substring(0, dotIx) : lastSegment;
			gfScope = new GFTagBasedScope(gfScope, moduleName, ignoreCase);
			
			// TODO John: This is taking very long with Functors (ie RGL)... how to speed up?
			gfScope.addTags(resource, entry.getValue());
		}
		return (gfScope == null) ? IScope.NULLSCOPE : gfScope;
	}
	
	/**
	 * Get the import URIs for a source file, possibly from cache
	 * @param resource
	 * @return
	 */
//	private Set<URI> getImportedURIs(final Resource resource) {
//		return cache.get(GFTagBasedGlobalScopeProvider.class.getName(), resource, new Provider<Set<URI>>(){
//			public Set<URI> get() {
//				return parseTagsFile(resource).keySet();
//			}
//		});
//	}
	
	/**
	 * Get list of all tags as a one-dimensional list, possibly from cache
	 * @param resource
	 * @return
	 */
//	private Collection<TagEntry> getTags(final Resource resource) {
//		return cache.get(GFTagBasedGlobalScopeProvider.class.getName(), resource, new Provider<Collection<TagEntry>>(){
//			public Collection<TagEntry> get() {
//				Collection<Collection<TagEntry>> tags2D = parseTagsFile(resource).values(); 
//				Collection<TagEntry> tags1D = new ArrayList<TagEntry>(); 
//				for (Collection<TagEntry> tagsItem : tags2D) {
//					tags1D.addAll(tagsItem);
//				}
//				return tags1D;
//			}
//		});
//	}
	
	/**
	 * Get list of all tags grouped by URI, possibly from cache
	 * @param resource
	 * @return
	 */
	private Hashtable<URI, Collection<TagEntry>> getURITagMap(final Resource resource) {
//		return cache.get(GFTagBasedGlobalScopeProvider.class.getName(), resource, new Provider<Hashtable<URI, Collection<TagEntry>>>(){
//			public Hashtable<URI, Collection<TagEntry>> get() {
				return parseTagsFile(resource); 
//			}
//		});
	}
	
	/**
	 * For a given resource, find its tags file and get a list of the all the files mentioned
	 * there, and return them as a list of unique URIs
	 * 
	 * @param resource
	 * @return
	 */
	private Hashtable<URI, Collection<TagEntry>> parseTagsFile(final Resource resource) {
		
		// Get module definition
		ModDef moduleDef;
		String moduleName;
		try {
			moduleDef = (ModDef)resource.getContents().get(0);
			moduleName = moduleDef.getType().getName().getS();
		} catch (Exception _) {
			// This means there's a mother syntax error (mid-way during editing). Just return quietly.
			return new Hashtable<URI, Collection<TagEntry>>();
		}
		
		// Find the corresponding tags file & parse it (1st pass)
		URI tagFileURI = libAgent.getTagsFile(resource, moduleName);
		Hashtable<URI, Collection<TagEntry>> uriTagMap = parseSingleTagsFile(tagFileURI, new Predicate<TagEntry>() {
			// Ignore references to self, ie local scope
			public boolean apply(TagEntry tag) {
				return !tag.getFile().endsWith(resource.getURI().lastSegment());
			}
		});
		
		// Iterate again to replace references to indir tags files with proper references
		Hashtable<URI, Collection<TagEntry>> resolvedUriTagMap = new Hashtable<URI, Collection<TagEntry>>(10);
		Iterator<URI> uriIter = uriTagMap.keySet().iterator();
		while(uriIter.hasNext()) {
			final URI uri = uriIter.next();
			// Just remove invalid URIs
			if (!EcoreUtil2.isValidUri(resource, uri)) {
				uriIter.remove();
			}
			// Resolve refs to other tags files and replace
			else if (uri.fileExtension().equals("gf-tags")) {
				resolvedUriTagMap.putAll( parseSingleTagsFile(uri, new Predicate<TagEntry>() {
					// Only include tags FROM the respective tags file (opposite of above)
					public boolean apply(TagEntry tag) {
//						return tag.getFile().endsWith(uri.lastSegment().substring(0, uri.lastSegment().length()-5));
						return !tag.getFile().endsWith(".gf-tags") ; //&& !tag.getType().equals("overload-type") ;
					}
				}) );
				uriIter.remove();
			}
		}
		
		// Combine them & return
		uriTagMap.putAll(resolvedUriTagMap);
		return uriTagMap;
	}
	
	
	private Hashtable<URI, Collection<TagEntry>> parseSingleTagsFile(URI tagFileURI, Predicate<TagEntry> includePredicate) {
		Hashtable<URI, Collection<TagEntry>> uriTagMap = new Hashtable<URI, Collection<TagEntry>>();
		try {
			InputStream is = uriConverter.createInputStream(tagFileURI);
			BufferedReader reader = new BufferedReader( new InputStreamReader(is) );
			String line;
			// Add everything into our arrays
			while ((line = reader.readLine()) != null) {
				TagEntry tag = new TagEntry(line);
				if (!includePredicate.apply(tag))
					continue;
				URI importURI = URI.createFileURI(tag.getFile());
				if (!uriTagMap.containsKey(importURI)) {
					uriTagMap.put(importURI, new ArrayList<TagEntry>());
				}
				uriTagMap.get(importURI).add(tag);
			}
			// Clean up
			reader.close();
			is.close();
		} catch (IOException e) {
			log.warn("Couldn't find tags file " + tagFileURI);
		}
		return uriTagMap;
	}
	
	
	@Inject
	private Provider<LoadOnDemandResourceDescriptions> loadOnDemandDescriptions;
	
	/**
	 * Gets the descriptions of resources listed in importUris
	 *
	 * @param resource the resource
	 * @param importUris the import uris
	 * @return the resource descriptions
	 */
	private IResourceDescriptions getResourceDescriptions(Resource resource, Collection<URI> importUris) {
		IResourceDescriptions result = getResourceDescriptions(resource);
		LoadOnDemandResourceDescriptions demandResourceDescriptions = loadOnDemandDescriptions.get();
		demandResourceDescriptions.initialize(result, importUris, resource);
		return demandResourceDescriptions;
	}
	
}
