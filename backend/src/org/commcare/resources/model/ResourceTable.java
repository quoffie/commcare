package org.commcare.resources.model;

import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.commcare.util.CommCareInstance;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.services.storage.StorageManager;

/**
 * TODO: We have too many vectors here. It's lazy and incorrect. ~everything
 * should be using iterators, not vectors; 
 * @author ctsims
 *
 */
public class ResourceTable {
	
	public static final String STORAGE_KEY_GLOBAL = "GLOBAL_RESOURCE_TABLE";
	private static final String STORAGE_KEY_TEMPORARY = "RESOURCE_TABLE_";

	private IStorageUtilityIndexed storage;
	
	private static ResourceTable global;

	/**
	 * For Serialization Only!
	 */
	public ResourceTable() {

	}

	public static ResourceTable RetrieveGlobalResourceTable() {
		if(global == null) {
			global = new ResourceTable();
			global.storage = (IStorageUtilityIndexed)StorageManager.getStorage(STORAGE_KEY_GLOBAL);
		} 
		//Not sure if this reference is actually a good idea, or whether we should 
		//get the storage link every time... For now, we'll reload storage each time
		System.out.println("Global Resource Table");
		System.out.println(global);
		return global;
	}

	public static ResourceTable CreateTemporaryResourceTable(String name) {
		ResourceTable table = new ResourceTable();
		IStorageUtilityIndexed storage = null; 
		String storageKey = STORAGE_KEY_TEMPORARY + name.toUpperCase();
		
		//Check if this table already exists, and return it if so.
		for(String utilityName : StorageManager.listRegisteredUtilities()) {
			if(utilityName.equals(storageKey)) {
				storage = (IStorageUtilityIndexed)StorageManager.getStorage(storageKey);
				table.storage = storage;
			}
		}
		//Otherwise, create a new one.
		if(storage == null) {
			StorageManager.registerStorage(storageKey, storageKey, Resource.class);
			table.storage = (IStorageUtilityIndexed)StorageManager.getStorage(storageKey);
		}
		System.out.println("Temporary Resource Table");
		System.out.println(table);
		return table;
	}

	public void removeResource(Resource resource) {
		storage.remove(resource);
	}
	
	public void addResource(Resource resource, ResourceInstaller initializer, String parentId, int status) throws StorageFullException{
		resource.setInstaller(initializer);
		resource.setParentId(parentId);
		addResource(resource, status);
	}
	
	public void addResource(Resource resource, ResourceInstaller initializer, String parentId) throws StorageFullException{
		addResource(resource, initializer, parentId, Resource.RESOURCE_STATUS_UNINITIALIZED);
	}
	
	public void addResource(Resource resource, int status) throws StorageFullException {
		resource.setStatus(status);
		try {
			//TODO: Check if it exists?
			if(resource.getID() != -1) {
				//Assume that we're going cross-table, so we need a new RecordId.
				resource.setID(-1);
				
				//Check to make sure that there's no existing GUID for this record.
				if(getResourceWithGuid(resource.getRecordGuid()) != null) {
					throw new RuntimeException("Why are you adding a record that already exists? Huh?");
				}
			}
			storage.write(resource);
		} catch (StorageFullException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Vector<Resource> getResourcesForParent(String parent) {
		Vector<Resource> v = new Vector<Resource>();
		for (Enumeration en = storage.getIDsForValue(Resource.META_INDEX_PARENT_GUID,parent).elements(); en.hasMoreElements();) {
			Resource r = (Resource) storage.read(((Integer)en.nextElement()).intValue());
			v.addElement(r);
		}
		return v;
	}

	public Resource getResourceWithId(String id) {
		try {
			return (Resource)storage.getRecordForValue(Resource.META_INDEX_RESOURCE_ID, id);
		} catch(NoSuchElementException nsee) {
			return null;
		}
	}
	
	public Resource getResourceWithGuid(String guid) {
		try {
		return (Resource)storage.getRecordForValue(Resource.META_INDEX_RESOURCE_GUID, guid);
		} catch(NoSuchElementException nsee) {
			return null;
		}
	}

	private Vector<Resource> GetResources() {
		Vector<Resource> v = new Vector<Resource>();
		for(IStorageIterator it = storage.iterate(); it.hasMore();) {
			Resource r = (Resource)it.nextRecord();
			v.addElement(r);
		}
		return v;
	}
	
	private Vector<Resource> GetResources(int status) {
		Vector<Resource> v = new Vector<Resource>();
		for(IStorageIterator it = storage.iterate(); it.hasMore();) {
			Resource r = (Resource)it.nextRecord();
			if(r.getStatus() == status) {
				v.addElement(r);
			}
		}
		return v;
	}

	private Vector<Resource> GetUnreadyResources() {
		Vector<Resource> v = new Vector<Resource>();
		for(IStorageIterator it = storage.iterate(); it.hasMore();) {
			Resource r = (Resource)it.nextRecord();
			//If the resource is installed, it doesn't need anything
			//If the resource is marked as ready for upgrade, it's ready
			//If the resource is marked as pending, it isn't capable of installation yet
			if (r.getStatus() != Resource.RESOURCE_STATUS_INSTALLED && r.getStatus() != Resource.RESOURCE_STATUS_UPGRADE && r.getStatus() != Resource.RESOURCE_STATUS_PENDING) {
				v.addElement(r);
			}
		}
		return v;
	}

	public boolean isReady() {
		if (GetUnreadyResources().size() > 0) {
			return false;
		} else {
			return true;
		}
	}
	
	public void commit(Resource r, int status, int version) throws UnresolvedResourceException{
		if(r.getVersion() == Resource.RESOURCE_VERSION_UNKNOWN) {
			//Try to update the version. 
			r.setVersion(version);
		} else {
			//Otherwise, someone screwed up
		}
		commit(r, status);
	}
	
	public void commit(Resource r, int status) throws UnresolvedResourceException{
		r.setStatus(status);
		commit(r);
	}
	
	public void commit(Resource r) throws UnresolvedResourceException{
		//r should already be in the storage table...
		try {
			storage.write(r);
			System.out.println(this);
		}
		catch(StorageFullException e) {
			throw new UnresolvedResourceException(r,"Ran out of space while updating resource definition...");
		}
	}

	public void prepareResources(ResourceTable master) throws UnresolvedResourceException {
		Vector<Resource> v = GetUnreadyResources();
		int round = -1;
		while (v.size() > 0) {
			round++;
			System.out.println("Preparing resources round " + round + ". " + v.size() + " resources remain");
			for (Resource r : v) {
				boolean upgrade = false;
				//Make a reference set for all invalid references (this will get filled in for us)
				Vector<Reference> invalid = new Vector<Reference>();
				
				//All operations regarding peers and master table
				if (master != null) {
					Resource peer = master.getResourceWithId(r.getResourceId());
					if (peer != null) {
						//TODO: For now we're assuming that Versions greater than the 
						//current are always acceptable
						if (!r.isNewer(peer)) {
							// This resource is already up to date in the master. Set 
							// its status to installed already.
							//TODO: If the resource has kids in this table who are pending
							//we should probably just remove them. Probably a non-issue/
							//no-op, though.
							commit(r,Resource.RESOURCE_STATUS_INSTALLED);
							continue;
							
						} else {
							upgrade = true;
						}
						invalid = ResourceTable.explodeLocalReferences(peer, master);
					}
				}
				
				// Vector<Reference> refs = explodeAllReferences(r, this,
				// master);

				boolean handled = false;
				
				//TODO: Possibly check if resource status is local and proceeding
				//to skip this huge (although in reality like one step) chunk

				for (ResourceLocation location : r.getLocations()) {
					if(handled) {
						break;
					}
					if (location.isRelative()) {
						for (Reference ref : explodeReferences(location, r,this, master)) {
							if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_LOCAL && invalid.contains(ref)) {
								System.out.println("Invalid (Stale) local reference attempt for: " + location.getLocation());
							} else {
								handled = r.getInstaller().install(r, location, ref, this, upgrade);
								if(handled) {
									break;
								}
							}
						}
					} else {
						try {
							handled = r.getInstaller().install(r, location, ReferenceManager._().DeriveReference(location.getLocation()), this, upgrade);
							if(handled) {
								break;
							}
						} catch(InvalidReferenceException ire) {
							ire.printStackTrace();
							//Continue until no resources can be found.
						}
					}
				}
				if(!handled) {
					throw new UnresolvedResourceException(r, "No external or local definition could be found for resource " + r.getResourceId()); 
				}

			}
			v = GetUnreadyResources();
		}
		
		//Wipe out any resources which are still pending. If they weren't updated by their 
		//parent, they aren't relevant.
		for(Resource stillPending : GetResources(Resource.RESOURCE_STATUS_PENDING)) {
			this.removeResource(stillPending);
		}
	}
	
	public boolean upgradeTable(ResourceTable incoming) throws UnresolvedResourceException {
		if(!incoming.isReady()) {
			return false;
		}
		//Everything incoming should be marked either ready or upgrade
		
		for(Resource r : incoming.GetResources()) {
			try {
			Resource peer = this.getResourceWithId(r.getResourceId());
			if(peer == null) {
				this.addResource(r, Resource.RESOURCE_STATUS_INSTALLED);
			} else {
				if(peer.getVersion() == r.getVersion()) {
					//Same resource. Don't do anything with it, it has no
					//children, so ID's don't need to change.
					//Technically resource locations could change, worth thinking
					//about for the future.
				}
				if(peer.getVersion() < r.getVersion()) {
					if(!peer.getInstaller().uninstall(peer, this, incoming)) {
						//TODO: This should be an exception
						return false;
					}
					if(r.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
						this.addResource(r,Resource.RESOURCE_STATUS_INSTALLED);
					} else if(r.getStatus() == Resource.RESOURCE_STATUS_UPGRADE) {
						if(r.getInstaller().upgrade(r,this)) {
							this.addResource(r,Resource.RESOURCE_STATUS_INSTALLED);
						}
					}
				}
			}
			} catch(StorageFullException sfe) {
				sfe.printStackTrace();
				throw new UnresolvedResourceException(r, "Resource Table Full while manipulating resource");
			}
		}
		
		System.out.println(this);
		
		// All of the incoming resources should now be installed and ready to roll.
		// The only thing left to do is run a cleanup on this table to clear out any
		// irrelevant resources. 
		// It's important to note that there is technically something that could go wrong here.
		// If the incoming table is lost before this step is completed, future descendents
		// may not know whether their children are relevant. As such, the installation
		// cannot really be marked completed (and the incoming table deleted) until
		// all deletions are made. 
		Vector<Resource> pendingDelete = GetResources(Resource.RESOURCE_STATUS_DELETE);
		while(pendingDelete.size() > 0) {
			for(Resource r : pendingDelete) {
				//Delete pending resource, possibly marking further resources for deletion
				r.getInstaller().uninstall(r, this, incoming);
			}
			pendingDelete = GetResources(Resource.RESOURCE_STATUS_DELETE);
			System.out.println("After of pending deletes:");
			System.out.println(this);
		}
		
		incoming.cleanup();
		return true;
	}
	
	public String toString() {
		String output = "";
		int ml = 0;
		for(Resource r : GetResources()) {
			String line = "| " + r.getResourceId() + " | " + r.getVersion() + " | " + getStatus(r.getStatus()) + " |\n";
			output += line;
			if(line.length() > ml) {
				ml = line.length();
			}
		}
		String cap = "";
		for(int i = 0 ; i < ml ; ++i ) {
			cap += "-";
		}
		return cap + "\n" + output + cap + "\n";
	}
	
	public String getStatus(int status) {
		switch(status) {
		case Resource.RESOURCE_STATUS_UNINITIALIZED:
			return "Uninitialized";
		case Resource.RESOURCE_STATUS_LOCAL:
			return "Local";
		case Resource.RESOURCE_STATUS_PENDING:
			return "Pending other Resource";
		case Resource.RESOURCE_STATUS_INSTALLED:
			return "Installed";
		case Resource.RESOURCE_STATUS_UPGRADE:
			return "Ready for Upgrade";
		case Resource.RESOURCE_STATUS_DELETE:
			return "Flagged for Deletion";
		default:
			return "Unknown";
		}
	}
	
	public void destroy() {
		clear();
		storage.destroy();
	}
	
	public void clear() {
		cleanup();
		storage.removeAll();
	}
	
	private void cleanup() {
		for(Resource r : GetResources()) {
			r.getInstaller().cleanup();
		}
	}
	
	public void initializeResources(CommCareInstance instance) throws ResourceInitializationException {
		for(Resource r : this.GetResources()) {
			ResourceInstaller i = r.getInstaller();
			if(i.requiresRuntimeInitialization()) {
				i.initialize(instance);
			}
		}
	}

	private static Vector<Reference> explodeLocalReferences(Resource r, ResourceTable t) {
		Vector<ResourceLocation> locations = r.getLocations();
		Vector<Reference> ret = new Vector<Reference>();
		for(ResourceLocation location : locations) {
			if(location.isRelative()) {
				if(r.hasParent()) {
					Resource parent = t.getResourceWithGuid(r.getParentId());
					if(parent != null) {
						//Get all local references for the parent
						Vector<Reference> parentRefs = explodeLocalReferences(parent, t);
						for(Reference context : parentRefs) {
							try{
								ret.addElement(ReferenceManager._().DeriveReference(location.getLocation(),context));
							}catch(InvalidReferenceException ire) {
								ire.printStackTrace();
							}
						}
					}
				}
			}
			else if(location.getAuthority() == Resource.RESOURCE_AUTHORITY_LOCAL) {
				try {
					ret.addElement(ReferenceManager._().DeriveReference(location.getLocation()));
				} catch (InvalidReferenceException e) {
					e.printStackTrace();
				}
			}
		}
		return ret;
	}
	
	private static Vector<Reference> explodeReferences(ResourceLocation location, Resource r, ResourceTable t, ResourceTable m) {
		int type = location.getAuthority();
		Vector<Reference> ret = new Vector<Reference>();
		if(r.hasParent()) {
			Resource parent = t.getResourceWithGuid(r.getParentId());
			
			//If the local table doesn't have the parent ref, try the master
			if(parent == null && m != null) {
				parent = m.getResourceWithGuid(r.getParentId());
			}
			if(parent != null) {
				//Get all local references for the parent
				Vector<Reference> parentRefs = explodeAllReferences(type, parent, t, m);
				for(Reference context : parentRefs) {
					try {
						ret.addElement(ReferenceManager._().DeriveReference(location.getLocation(),context));
					} catch (InvalidReferenceException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return ret;
	}
	
	private static Vector<Reference> explodeAllReferences(int type, Resource r, ResourceTable t, ResourceTable m) {
		Vector<ResourceLocation> locations = r.getLocations();
		Vector<Reference> ret = new Vector<Reference>();
		for(ResourceLocation location : locations) {
			if(location.getAuthority() == type) {
			 if(location.isRelative()) {
				if(r.hasParent()) {
					Resource parent = t.getResourceWithGuid(r.getParentId());
					
					//If the local table doesn't have the parent ref, try the master
					if(parent == null) {
						parent = m.getResourceWithGuid(r.getParentId());
					}
					if(parent != null) {
						//Get all local references for the parent
						Vector<Reference> parentRefs = explodeAllReferences(type, parent, t, m);
						for(Reference context : parentRefs) {
							try {
								ret.addElement(ReferenceManager._().DeriveReference(location.getLocation(),context));
							} catch (InvalidReferenceException e) {
								e.printStackTrace();
							}
						}
					}
				}
			} else  {
				try {
					ret.addElement(ReferenceManager._().DeriveReference(location.getLocation()));
				} catch (InvalidReferenceException e) {
					e.printStackTrace();
				}
			}
		}
		}
		return ret;
	}
	
}