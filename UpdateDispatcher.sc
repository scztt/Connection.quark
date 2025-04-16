UpdateDispatcher {
	classvar <dispatcherDict;

	var <dispatchTable;
	var <connection;

	*initClass {
		dispatcherDict = IdentityDictionary();
	}

	*new {
		|object|
		^dispatcherDict.atCreate(object, { super.new.init(object) })
	}

	*free {
		|object|
		dispatcherDict[object].free;
	}

	*addItem {
		|item|
		UpdateDispatcher(item.object).addItem(item);
	}

	addItem {
		|item|
		dispatchTable ?? {
			dispatchTable = IdentityDictionary();
			connection.connect();
		};
		dispatchTable[item.key] = item;
	}

	*removeItem {
		|item|
		var dispatcher = UpdateDispatcher(item.object).removeItem(item);
	}

	removeItem {
		|item|
		dispatchTable[item.key] = nil;
		if (dispatchTable.size == 0) {
			this.free();
		}
	}

	init {
		|object|
		dispatchTable = IdentityDictionary();
		connection = object.connectTo(this);
	}

	free {
		this.class.dispatcherDict[this.class.dispatcherDict.findKeyForValue(this)] = nil; // clear self
		connection.free();
		connection = dispatchTable = nil;
	}

	at {
		|key|
		dispatchTable ?? {
			dispatchTable = IdentityDictionary();
			connection.connect();
		};
		^dispatchTable.atCreate(key, { UpdateDispatcherItem(connection.object, key) });
	}

	update {
		|obj, changed ...args|
		var item = dispatchTable[changed];

		item !? {
			item.update(obj, changed, *args);
		}
	}

	connectionTraceString {
		|obj, what|
		var connection = dispatchTable[obj];
		^connection !? {
			connection.dependant.connectionTraceString(obj, what)
		} ?? {
			"%(%) - no target".format(this.class, this.identityHash)
		}
	}
}

UpdateDispatcherItem : UpdateForwarder {
	var <object, <key;

	*new {
		|object, key|
		^super.new.init(object, key)
	}

	init {
		|inObject, inKey|
		object = inObject;
		key = inKey;
	}

	onDependantsNotEmpty {
		UpdateDispatcher.addItem(this);
	}

	onDependantsEmpty {
		UpdateDispatcher.removeItem(this);
	}

	onDependantAdded {
		|dependant|
		object.onSignalDependantAdded(key, dependant);
	}

	connectionFree {
		this.release();
		object = nil;
		key = nil;
	}

	uniqueConnectionAt {
		|name|
		^UniqueConnections.at(object, key, name)
	}

	uniqueConnectionPut {
		|name, value|
		^UniqueConnections.put(object, key, name, value)
	}
}